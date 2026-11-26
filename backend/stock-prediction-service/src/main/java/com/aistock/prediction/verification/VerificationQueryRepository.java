package com.aistock.prediction.verification;
import com.fasterxml.jackson.databind.*;import com.fasterxml.jackson.databind.node.*;
import java.time.*;import java.sql.*;import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;import org.springframework.transaction.support.TransactionTemplate;import org.springframework.http.HttpStatus;
/** Read only, single-service, consistent view of an owner's immutable evidence. */
public final class VerificationQueryRepository {
 private final JdbcTemplate jdbc;private final ObjectMapper mapper;private final Clock clock;private final int hour;private final TransactionTemplate tx;
 public VerificationQueryRepository(JdbcTemplate jdbc,ObjectMapper mapper,Clock clock,int hour){this.jdbc=jdbc;this.mapper=mapper;this.clock=clock;this.hour=hour;tx=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));tx.setReadOnly(true);tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);tx.setTimeout(5);}
 public JsonNode list(UUID user,UUID prediction,int page){if(page<1||page>10000)throw VerificationRepository.error("VERIFICATION_PAGE_INVALID",HttpStatus.BAD_REQUEST);return tx.execute(t->{var snapshot=owned(user,prediction);var root=mapper.createObjectNode();var now=clock.instant();var local=now.atZone(ZoneId.of("Asia/Shanghai"));var cutoff=local.toLocalDate().minusDays(local.getHour()<hour?1:0);root.put("predictionId",prediction.toString()).put("page",page).put("size",20).put("cutoffDate",cutoff.toString());var expected=root.putArray("expectedPoints");for(var p:snapshot.path("prediction").path("points")){var node=expected.addObject();node.put("step",p.path("step").asInt()).put("targetDate",p.path("targetDate").asText()).put("referencePrice",p.path("referencePrice").asText()).put("state",LocalDate.parse(p.path("targetDate").asText()).isAfter(cutoff)?"WAITING":"PENDING");}
  root.put("total",jdbc.queryForObject("select count(*) from forecast_verification_version where prediction_id=?",Long.class,prediction.toString()));var items=root.putArray("items");jdbc.query("select * from forecast_verification_version where prediction_id=? order by created_at desc,version_id desc limit 20 offset ?",rs->{var item=entry(rs,false);item.put("state",parse(rs.getString("evaluation_json")).path("state").asText()).put("verifiedCount",parse(rs.getString("evaluation_json")).path("verifiedCount").asInt());items.add(item);},prediction.toString(),(page-1)*20);
  var heads=jdbc.queryForList("select current_version_id from forecast_verification_head where prediction_id=? and current_version_id is not null",String.class,prediction.toString());root.putNull("currentVersionId");root.putNull("current");if(!heads.isEmpty()){root.put("currentVersionId",heads.get(0));root.set("current",version(prediction,UUID.fromString(heads.get(0))));}
  root.putNull("latestAttempt");jdbc.query("select state,error_code,created_at,finished_at,(lease_until<=?) as expired from forecast_verification_operation where prediction_id=? order by sequence_id desc limit 1",rs->{var attempt=root.putObject("latestAttempt");attempt.put("state",rs.getString("state")).put("errorCode",rs.getString("error_code")).put("createdAt",rs.getTimestamp("created_at").toInstant().toString());var finished=rs.getTimestamp("finished_at");if(finished==null)attempt.putNull("finishedAt");else attempt.put("finishedAt",finished.toInstant().toString());attempt.put("expired","RUNNING".equals(rs.getString("state"))&&rs.getBoolean("expired"));},Timestamp.from(now),prediction.toString());return root;});}
 private record FusionRow(String realizationId,LocalDate dueThrough,JsonNode body){}
 public Optional<VerificationRecords.Evidence> sharedFusionEvidence(UUID user,List<UUID> predictions) {
  if(predictions.size()<2||predictions.size()>3||new HashSet<>(predictions).size()!=predictions.size())return Optional.empty();
  var parameters=new ArrayList<String>();
  parameters.add(user.toString());
  parameters.add(VerificationRecords.RULE);
  predictions.forEach(id->parameters.add(id.toString()));
  var placeholders=String.join(",",Collections.nCopies(predictions.size(),"?"));
  var rows=jdbc.query("""
      SELECT v.realization_version_id,v.due_through,v.evidence_json
      FROM forecast_request f JOIN forecast_verification_head h ON h.prediction_id=f.prediction_id
      JOIN forecast_verification_version v ON v.prediction_id=f.prediction_id AND v.version_id=h.current_version_id
      WHERE f.user_id=? AND f.state='SUCCEEDED' AND f.hidden=false AND v.rule_version=? AND f.prediction_id IN (
      """+placeholders+")",(row,index)->new FusionRow(row.getString(1),row.getDate(2).toLocalDate(),parse(row.getString(3))),
      parameters.toArray());
  if(rows.size()!=predictions.size())return Optional.empty();
  var first=rows.get(0);
  var hash=first.body().path("contentHash");
  VerificationRecords.Evidence common=null;
  for(var row:rows){
   if(!first.realizationId().equals(row.realizationId())||!first.dueThrough().equals(row.dueThrough())
       ||!hash.equals(row.body().path("contentHash")))return Optional.empty();
   var evidence=VerificationEvidence.read(mapper,row.body(),new VerificationRequest(predictions.get(0),row.dueThrough(),null),clock);
   if(!row.realizationId().equals(evidence.versionId())||common!=null&&!common.equals(evidence))return Optional.empty();
   common=evidence;
  }
  return Optional.ofNullable(common);
 }
 public JsonNode detail(UUID user,UUID prediction,UUID version){return tx.execute(t->{owned(user,prediction);return version(prediction,version);});}
 private JsonNode owned(UUID user,UUID prediction){var rows=jdbc.queryForList("select state,snapshot_json from forecast_request where user_id=? and prediction_id=?",user.toString(),prediction.toString());if(rows.isEmpty())throw VerificationRepository.error("RESOURCE_NOT_FOUND",HttpStatus.NOT_FOUND);if(!"SUCCEEDED".equals(rows.get(0).get("state")))throw VerificationRepository.error("VERIFICATION_PREDICTION_INVALID",HttpStatus.CONFLICT);return parse((String)rows.get(0).get("snapshot_json"));}
 private JsonNode version(UUID prediction,UUID version){var rows=jdbc.query("select * from forecast_verification_version where prediction_id=? and version_id=?",(rs,n)->entry(rs,true),prediction.toString(),version.toString());if(rows.isEmpty())throw VerificationRepository.error("RESOURCE_NOT_FOUND",HttpStatus.NOT_FOUND);return rows.get(0);}
 private ObjectNode entry(ResultSet rs,boolean full)throws SQLException{var n=mapper.createObjectNode();n.put("verificationVersionId",rs.getString("version_id")).put("realizationVersionId",rs.getString("realization_version_id")).put("ruleVersion",rs.getString("rule_version")).put("createdAt",rs.getTimestamp("created_at").toInstant().toString()).put("dueThrough",rs.getDate("due_through").toLocalDate().toString());if(full){n.set("evidence",parse(rs.getString("evidence_json")));n.set("result",parse(rs.getString("evaluation_json")));}return n;}
 private JsonNode parse(String json){try{return mapper.readTree(json);}catch(Exception e){throw VerificationRepository.error("VERIFICATION_RECORD_INVALID",HttpStatus.INTERNAL_SERVER_ERROR);}}
}
