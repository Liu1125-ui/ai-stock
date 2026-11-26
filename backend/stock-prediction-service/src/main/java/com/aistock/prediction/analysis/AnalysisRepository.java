package com.aistock.prediction.analysis;
import com.aistock.common.BusinessException;import com.aistock.prediction.maas.MaasOperationGate;import com.fasterxml.jackson.databind.*;import org.springframework.http.HttpStatus;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.jdbc.datasource.DataSourceTransactionManager;import org.springframework.transaction.support.TransactionTemplate;import java.time.*;import java.util.*;import java.security.*;import java.nio.charset.StandardCharsets;import static com.aistock.prediction.maas.MaasOperationGate.*;
public final class AnalysisRepository {
 private static final String KIND="STOCK_ANALYSIS";private final JdbcTemplate jdbc;private final ObjectMapper m;private final TransactionTemplate tx;private final MaasOperationGate gate;
 public record Claim(boolean execute,JsonNode receipt,long generation){
  public Claim(boolean execute,JsonNode receipt){this(execute,receipt,1);}
 }
 public AnalysisRepository(JdbcTemplate jdbc,ObjectMapper m){this.jdbc=jdbc;this.m=m;this.gate=new MaasOperationGate(jdbc);tx=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));tx.setTimeout(5);}
 /** Admission commits before any evidence or model IO. An operation is never restarted in place. */
 public Claim begin(UUID user,AnalysisInput in,Instant now){return tx.execute(s->{
  gate.lock(now);var prior=replay(user,in,now);if(prior!=null)return new Claim(false,prior,prior.path("executionGeneration").asLong());
  var context=m.createObjectNode();if(in.predictionId()!=null)context.putObject("prediction").put("predictionId",in.predictionId().toString());
  jdbc.update("insert into stock_analysis_operation(operation_id,user_id,instrument_id,range_from,range_to,market_version_id,request_hash,context_hash,context_json,state,created_at,lease_until,context_ready) values(?,?,?,?,?,?,?,?,?,'RUNNING',?,?,false)",in.operationId().toString(),user.toString(),in.instrumentId(),in.from(),in.to(),in.marketVersionId().toString(),hash(in),hash(in),json(context),ts(now),ts(now.plusSeconds(50)));
  return new Claim(true,receipt(operations(in.operationId()).get(0),now),1);
 });}
 public JsonNode replay(UUID user,AnalysisInput in,Instant now){return tx.execute(s->{
  gate.lock(now);var rows=operations(in.operationId());if(rows.isEmpty())return null;var row=rows.get(0);
  if(!user.toString().equals(row.get("user_id"))||!hash(in).equals(row.get("request_hash")))throw conflict();
  expire(in.operationId(),now);return receipt(operations(in.operationId()).get(0),now);
 });}
 /** Attach the first verified evidence snapshot once; only then acquire the paid-model slot. */
 public Claim bindFacts(UUID op,long generation,AnalysisFacts.Facts facts,Instant now){return tx.execute(s->{
  gate.lock(now);expire(op,now);var row=execution(op,generation);
  if(!"RUNNING".equals(row.get("state"))||ready(row))return new Claim(false,receipt(row,now),generation);
  var saved=jdbc.queryForList("select analysis_id from stock_analysis_result where user_id=? and context_hash=?",String.class,row.get("user_id"),facts.hash());
  String id=saved.isEmpty()?null:saved.get(0);if(id==null)gate.lockAndAcquire(KIND,op,now);
  int changed=jdbc.update("update stock_analysis_operation set context_hash=?,context_json=?,context_ready=true,state=?,analysis_id=?,lease_until=?,finished_at=?,fencing_revision=fencing_revision+1 where operation_id=? and execution_generation=? and state='RUNNING' and context_ready=false",facts.hash(),json(facts.context()),id==null?"RUNNING":"SUCCEEDED",id,ts(now.plusSeconds(50)),id==null?null:ts(now),op.toString(),generation);
  if(changed!=1)throw conflict();return new Claim(id==null,receipt(operations(op).get(0),now),generation);
 });}
 /** Local compatibility entry used by repository callers; no dependency call occurs here. */
 public Claim claim(UUID user,AnalysisInput in,AnalysisFacts.Facts facts,Instant now){return tx.execute(s->{var claim=begin(user,in,now);return claim.execute()?bindFacts(in.operationId(),claim.generation(),facts,now):claim;});}
 public JsonNode complete(UUID op,JsonNode paragraphs,String error,Instant now){return complete(op,1,paragraphs,error,now);}
 public JsonNode complete(UUID op,long generation,JsonNode paragraphs,String error,Instant now){return tx.execute(s->{
  var slot=gate.lock(now);expire(op,now);var r=execution(op,generation);if(!"RUNNING".equals(r.get("state")))return receipt(r,now);
  if(ready(r)&&!gate.owns(slot,KIND,op)||error==null&&!ready(r))throw conflict();
  String code=error;String id=null;
  if(code==null){id=UUID.randomUUID().toString();jdbc.update("insert into stock_analysis_result(analysis_id,user_id,instrument_id,range_from,range_to,market_version_id,context_hash,context_json,paragraphs_json,created_at) values(?,?,?,?,?,?,?,?,?,?)",id,r.get("user_id"),r.get("instrument_id"),r.get("range_from"),r.get("range_to"),r.get("market_version_id"),r.get("context_hash"),r.get("context_json"),json(paragraphs),ts(now));}
  else if(!code.matches("[A-Z0-9_]{1,64}"))code="AI_UNAVAILABLE";
  int changed=jdbc.update("update stock_analysis_operation set state=?,analysis_id=?,error_code=?,finished_at=?,fencing_revision=fencing_revision+1 where operation_id=? and execution_generation=? and state='RUNNING'",id==null?"FAILED":"SUCCEEDED",id,code,ts(now),op.toString(),generation);
  if(changed!=1)throw conflict();gate.release(KIND,op);return receipt(operations(op).get(0),now);
 });}
 private Map<String,Object> execution(UUID op,long generation){var rows=operations(op);if(rows.isEmpty()||generation<1||((Number)rows.get(0).get("execution_generation")).longValue()!=generation)throw conflict();return rows.get(0);}
 private boolean ready(Map<String,Object> row){return Boolean.TRUE.equals(row.get("context_ready"))||row.get("context_ready") instanceof Number n&&n.intValue()==1;}
 private void expire(UUID op,Instant now){int changed=jdbc.update("update stock_analysis_operation set state='FAILED',error_code='AI_INTERRUPTED',finished_at=?,fencing_revision=fencing_revision+1 where operation_id=? and state='RUNNING' and lease_until<=?",ts(now),op.toString(),ts(now));if(changed==1)gate.release(KIND,op);}
 /** Internal proof data comes from the actual irreversible operation, never a caller-supplied state. */
 public JsonNode executionTerminal(UUID op,long generation,Instant now){return tx.execute(s->{
  gate.lock(now);expire(op,now);var row=execution(op,generation);
  if(!Set.of("SUCCEEDED","FAILED").contains(row.get("state"))||row.get("finished_at")==null)throw com.aistock.prediction.marketuse.MarketUseClient.unavailable();
  return m.createObjectNode().put("state",row.get("state").toString()).put("terminalAt",instant(row.get("finished_at")).toString()).put("fencingRevision",row.get("fencing_revision").toString()).put("resultIdentity",row.get("analysis_id")==null?op.toString():row.get("analysis_id").toString());
 });}
 public JsonNode get(UUID user,UUID id){var rows=jdbc.queryForList("select * from stock_analysis_result where analysis_id=? and user_id=?",id.toString(),user.toString());if(rows.isEmpty())throw new BusinessException("RESOURCE_NOT_FOUND","未找到分析记录",HttpStatus.NOT_FOUND);return result(rows.get(0));}
 public JsonNode list(UUID user,String stock,LocalDate from,LocalDate to,int page,Instant now){var root=m.createObjectNode().put("page",page);var list=root.putArray("items");jdbc.queryForList("select * from stock_analysis_result where user_id=? and instrument_id=? and range_from=? and range_to=? order by sequence_id desc limit 10 offset ?",user.toString(),stock,from,to,(page-1)*10).forEach(r->list.add(result(r)));root.put("total",jdbc.queryForObject("select count(*) from stock_analysis_result where user_id=? and instrument_id=? and range_from=? and range_to=?",Long.class,user.toString(),stock,from,to));var ops=jdbc.queryForList("select * from stock_analysis_operation where user_id=? and instrument_id=? and range_from=? and range_to=? order by sequence_id desc limit 1",user.toString(),stock,from,to);root.set("latestAttempt",ops.isEmpty()?m.nullNode():receipt(ops.get(0),now));return root;}
 private List<Map<String,Object>> operations(UUID id){return jdbc.queryForList("select * from stock_analysis_operation where operation_id=?",id.toString());}
 private JsonNode receipt(Map<String,Object> r,Instant now){var n=m.createObjectNode();for(var names:List.of(new String[]{"operation_id","operationId"},new String[]{"analysis_id","analysisId"},new String[]{"context_hash","contextHash"},new String[]{"state","state"},new String[]{"error_code","errorCode"})){var v=r.get(names[0]);if(v==null)n.putNull(names[1]);else n.put(names[1],v.toString());}n.put("instrumentId",r.get("instrument_id").toString()).put("marketVersionId",r.get("market_version_id").toString()).put("from",r.get("range_from").toString()).put("to",r.get("range_to").toString());try{var prediction=m.readTree(r.get("context_json").toString()).path("prediction").path("predictionId");if(prediction.isTextual())n.put("predictionId",prediction.asText());else n.putNull("predictionId");}catch(Exception e){throw AnalysisFacts.invalid();}if(!ready(r))n.putNull("contextHash");n.put("executionGeneration",((Number)r.get("execution_generation")).longValue()).put("fencingRevision",((Number)r.get("fencing_revision")).longValue()).put("contextReady",ready(r));n.put("leaseUntil",instant(r.get("lease_until")).toString()).put("expired","RUNNING".equals(r.get("state"))&&!now.isBefore(instant(r.get("lease_until"))));return n;}
 private JsonNode result(Map<String,Object> r){var n=m.createObjectNode().put("analysisId",r.get("analysis_id").toString()).put("createdAt",instant(r.get("created_at")).toString()).put("contextHash",r.get("context_hash").toString());try{n.set("context",m.readTree(r.get("context_json").toString()));n.set("paragraphs",m.readTree(r.get("paragraphs_json").toString()));}catch(Exception e){throw AnalysisFacts.invalid();}return n;}
 private String hash(AnalysisInput in){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((in.instrumentId()+"|"+in.from()+"|"+in.to()+"|"+in.marketVersionId()+(in.predictionId()==null?"":"|"+in.predictionId())).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private String json(Object v){try{return m.writeValueAsString(v);}catch(Exception e){throw AnalysisFacts.invalid();}}
 private BusinessException conflict(){return new BusinessException("AI_REQUEST_CONFLICT","分析请求编号与条件不一致",HttpStatus.CONFLICT);}
}