package com.aistock.prediction.portfolioanalysis;
import com.fasterxml.jackson.databind.*;import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.*;import java.sql.Timestamp;import java.util.*;import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.jdbc.datasource.DataSourceTransactionManager;import org.springframework.transaction.support.TransactionTemplate;
import static com.aistock.prediction.portfolioanalysis.PortfolioRecords.*;
/** Local prediction DB transactions only. No network calls or request credentials. */
public final class PortfolioRepository {
 public record Page(List<JsonNode> items,String upper,String nextBefore){}
 private final JdbcTemplate jdbc;private final ObjectMapper mapper;private final TransactionTemplate tx;
 public PortfolioRepository(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;tx=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));tx.setTimeout(5);}
 public Claim claim(UUID owner,UUID request,Instant now,LocalDate asOf,BigDecimal threshold){
  if(owner==null||request==null||now==null||asOf==null||threshold==null||threshold.signum()<=0||threshold.compareTo(new BigDecimal("100"))>0||threshold.scale()>6)throw PortfolioErrors.invalid();
  return tx.execute(s->{var generation=UUID.randomUUID();
   int inserted=jdbc.update("insert ignore into portfolio_analysis_operation(owner_id,request_id,state,accepted_at,deadline_at,as_of_date,threshold_percent,formula_version,lease_generation,lease_until) values(?,?,'RUNNING',?,?,?,?,?,?,?)",owner.toString(),request.toString(),ts(now),ts(now.plusSeconds(30)),asOf,threshold,FORMULA,generation.toString(),ts(now.plusSeconds(45)));
   var row=locked(owner,request);expire(owner,request,row,now);row=locked(owner,request);
   return new Claim(inserted==1&&"RUNNING".equals(row.get("state")),inserted==1?generation:null,receipt(row),inserted==0);
  });
 }
 public Operation find(UUID owner,UUID request,Instant now){return tx.execute(s->{var rows=rows(owner,request,true);if(rows.isEmpty())return null;expire(owner,request,rows.get(0),now);return receipt(locked(owner,request));});}
 public Operation operation(UUID owner,UUID request,Instant now){var op=find(owner,request,now);if(op==null)throw PortfolioErrors.missing();return op;}
 public Operation complete(UUID owner,UUID request,UUID generation,Calculation calculation,PositionSnapshot snapshot,QuoteBatch quotes,Clock clock){
  String snapshotJson=json(snapshot,262144),quotesJson=json(quotes,262144);
  if(calculation==null||snapshot==null||!owner.equals(snapshot.ownerId()))throw PortfolioErrors.invalid();
  new PortfolioSourceVerifier().verify(snapshot,quotes,owner,calculation.asOfDate());
  return tx.execute(s->{
   var row=ownedGeneration(owner,request,generation);Instant afterLock=clock.instant();
   if(expire(owner,request,row,afterLock))return receipt(locked(owner,request));
   if(!calculation.asOfDate().equals(((java.sql.Date)row.get("as_of_date")).toLocalDate())||!FORMULA.equals(calculation.formulaVersion())||new BigDecimal(calculation.concentrationThresholdPercent()).compareTo((BigDecimal)row.get("threshold_percent"))!=0||!snapshot.bookRevision().equals(calculation.bookRevision()))throw PortfolioErrors.invalid();
   UUID id=UUID.randomUUID();ObjectNode result=mapper.valueToTree(calculation);result.put("analysisId",id.toString()).put("createdAt",afterLock.toString());
   String resultJson=json(result,524288);
   jdbc.update("insert into portfolio_analysis_result(analysis_id,owner_id,snapshot_json,quotes_json,result_json,source_hash,created_at) values(?,?,?,?,?,?,?)",id.toString(),owner.toString(),snapshotJson,quotesJson,resultJson,calculation.sourceHash(),ts(afterLock));
   Instant beforeUpdate=clock.instant();
   int updated=jdbc.update("update portfolio_analysis_operation set state='SUCCEEDED',fencing_revision=fencing_revision+1,result_id=?,finished_at=? where owner_id=? and request_id=? and state='RUNNING' and lease_generation=? and deadline_at>? and lease_until>?",id.toString(),ts(beforeUpdate),owner.toString(),request.toString(),generation.toString(),ts(beforeUpdate),ts(beforeUpdate));
   if(updated!=1)throw PortfolioErrors.conflict();return receipt(locked(owner,request));
  });
 }
 public Operation fail(UUID owner,UUID request,UUID generation,String error,Clock clock){return tx.execute(s->{
  var row=ownedGeneration(owner,request,generation);Instant afterLock=clock.instant();if(expire(owner,request,row,afterLock))return receipt(locked(owner,request));
  String safe=error!=null&&error.matches("[A-Z0-9_]{1,64}")?error:"PORTFOLIO_SOURCE_UNAVAILABLE";
  Instant beforeUpdate=clock.instant();if(expire(owner,request,row,beforeUpdate))return receipt(locked(owner,request));
  int updated=jdbc.update("update portfolio_analysis_operation set state='FAILED',fencing_revision=fencing_revision+1,error_code=?,finished_at=? where owner_id=? and request_id=? and state='RUNNING' and lease_generation=? and deadline_at>? and lease_until>?",safe,ts(beforeUpdate),owner.toString(),request.toString(),generation.toString(),ts(beforeUpdate),ts(beforeUpdate));
  if(updated!=1)throw PortfolioErrors.conflict();return receipt(locked(owner,request));
 });}
 public JsonNode get(UUID owner,UUID id){var found=jdbc.queryForList("select result_json from portfolio_analysis_result where owner_id=? and analysis_id=?",String.class,owner.toString(),id.toString());if(found.isEmpty())throw PortfolioErrors.missing();return parse(found.get(0));}
 public Page list(UUID owner,String before,String upper){
  if(before!=null&&upper==null)throw PortfolioErrors.invalid();Long b=number(before),u=number(upper);if(b!=null&&b>u)throw PortfolioErrors.invalid();
  if(u==null)u=jdbc.queryForObject("select max(history_seq) from portfolio_analysis_result where owner_id=?",Long.class,owner.toString());if(u==null)return new Page(List.of(),null,null);
  var found=b==null?jdbc.queryForList("select history_seq,result_json from portfolio_analysis_result where owner_id=? and history_seq<=? order by history_seq desc limit 21",owner.toString(),u):jdbc.queryForList("select history_seq,result_json from portfolio_analysis_result where owner_id=? and history_seq<=? and history_seq<? order by history_seq desc limit 21",owner.toString(),u,b);
  var result=new ArrayList<JsonNode>();for(var row:found.subList(0,Math.min(20,found.size()))){var node=(ObjectNode)parse((String)row.get("result_json"));node.remove("lines");result.add(node);}
  return new Page(List.copyOf(result),u.toString(),found.size()>20?found.get(19).get("history_seq").toString():null);
 }
 /** Locks the actual executable owner before preparing a durable market selector. */
 public com.aistock.common.marketlifecycle.MarketUseRecords.Selector useInput(UUID owner,UUID request,UUID execution,List<String> stocks,Clock clock){return tx.execute(s->{var row=ownedGeneration(owner,request,execution);if(expire(owner,request,row,clock.instant()))throw com.aistock.prediction.marketuse.MarketUseClient.unavailable();return com.aistock.common.marketlifecycle.MarketLifecycleContract.selector(new com.aistock.common.marketlifecycle.MarketUseRecords.Selector("CURRENT_HEADS",null,null,stocks));});}
 /** Reading time after the owner lock makes an expired proof physically irreversible. */
 public JsonNode executionTerminal(UUID execution,Clock clock){return tx.execute(s->{var bindings=jdbc.queryForList("select owner_id,request_id from portfolio_market_use where execution_id=?",execution.toString());if(bindings.size()!=1)throw com.aistock.prediction.marketuse.MarketUseClient.unavailable();var binding=bindings.get(0);UUID owner=UUID.fromString(binding.get("owner_id").toString()),request=UUID.fromString(binding.get("request_id").toString());var row=locked(owner,request);if(!execution.toString().equals(row.get("lease_generation")))throw com.aistock.prediction.marketuse.MarketUseClient.unavailable();expire(owner,request,row,clock.instant());row=locked(owner,request);if(!Set.of("SUCCEEDED","FAILED").contains(row.get("state"))||row.get("finished_at")==null)throw com.aistock.prediction.marketuse.MarketUseClient.unavailable();var n=mapper.createObjectNode();n.put("state",row.get("state").toString()).put("terminalAt",instant(row.get("finished_at")).toString()).put("fencingRevision",row.get("fencing_revision").toString()).put("resultIdentity",row.get("result_id")==null?execution.toString():row.get("result_id").toString());return n;});}
 private Map<String,Object> ownedGeneration(UUID owner,UUID request,UUID generation){var row=locked(owner,request);if(!"RUNNING".equals(row.get("state"))||generation==null||!generation.toString().equals(row.get("lease_generation")))throw PortfolioErrors.conflict();return row;}
 private boolean expire(UUID owner,UUID request,Map<String,Object> row,Instant now){
  if(!"RUNNING".equals(row.get("state"))||now.isBefore(instant(row.get("deadline_at")))&&now.isBefore(instant(row.get("lease_until"))))return false;
  jdbc.update("update portfolio_analysis_operation set state='FAILED',fencing_revision=fencing_revision+1,error_code='PORTFOLIO_OPERATION_EXPIRED',finished_at=? where owner_id=? and request_id=? and state='RUNNING'",ts(now),owner.toString(),request.toString());return true;
 }
 private Map<String,Object> locked(UUID owner,UUID request){var found=rows(owner,request,true);if(found.isEmpty())throw PortfolioErrors.missing();return found.get(0);}
 private List<Map<String,Object>> rows(UUID owner,UUID request,boolean lock){return jdbc.queryForList("select * from portfolio_analysis_operation where owner_id=? and request_id=?"+(lock?" for update":""),owner.toString(),request.toString());}
 private Operation receipt(Map<String,Object> r){return new Operation(UUID.fromString(r.get("request_id").toString()),r.get("state").toString(),r.get("result_id")==null?null:UUID.fromString(r.get("result_id").toString()),(String)r.get("error_code"),instant(r.get("accepted_at")),instant(r.get("deadline_at")));}
 private Long number(String text){if(text==null)return null;try{if(!text.matches("[1-9][0-9]{0,18}"))throw PortfolioErrors.invalid();return Long.parseLong(text);}catch(NumberFormatException e){throw PortfolioErrors.invalid();}}
 private String json(Object value,int limit){try{var bytes=mapper.writeValueAsBytes(value);if(bytes.length>limit)throw PortfolioErrors.invalid();return new String(bytes,java.nio.charset.StandardCharsets.UTF_8);}catch(com.aistock.common.BusinessException e){throw e;}catch(Exception e){throw PortfolioErrors.invalid();}}
 private JsonNode parse(String text){try{return mapper.readTree(text);}catch(Exception e){throw PortfolioErrors.unavailable();}}
 private Timestamp ts(Instant instant){return Timestamp.from(instant);}
 private Instant instant(Object value){return value instanceof Timestamp t?t.toInstant():((LocalDateTime)value).toInstant(ZoneOffset.UTC);}
}
