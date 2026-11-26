package com.aistock.prediction.forecast;
import com.aistock.common.BusinessException;
import com.aistock.prediction.modelcatalog.ModelRecords.Version;
import com.fasterxml.jackson.databind.*;
import java.sql.*;import java.time.*;import java.util.*;import java.util.function.Supplier;
import org.springframework.http.HttpStatus;import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;import org.springframework.transaction.support.TransactionTemplate;
import static com.aistock.prediction.forecast.ForecastRecords.*;
import com.aistock.prediction.forecast.ForecastRecords.Record;
public final class ForecastRepository {
 private final JdbcTemplate jdbc;private final ObjectMapper mapper;private final Clock clock;private final TransactionTemplate tx;
 public ForecastRepository(JdbcTemplate jdbc,ObjectMapper mapper,Clock clock){this.jdbc=jdbc;this.mapper=mapper;this.clock=clock;tx=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));}
 private void guard(UUID user){jdbc.update("insert into forecast_user_guard(user_id) values(?) on duplicate key update user_id=user_id",user.toString());jdbc.queryForObject("select user_id from forecast_user_guard where user_id=? for update",String.class,user.toString());expire(user);}
 private void expire(UUID user){jdbc.update("update forecast_request set state='FAILED',error_code='PREDICTION_INTERRUPTED',finished_at=?,fencing_revision=fencing_revision+1 where user_id=? and state='RUNNING' and expires_at<=?",Timestamp.from(clock.instant()),user.toString(),Timestamp.from(clock.instant()));}
 public Claim claim(UUID user,Input input,String hash,Supplier<Version> model){return tx.execute(status->{guard(user);var rows=jdbc.query("select * from forecast_request where user_id=? and operation_id=?",(r,n)->record(r),user.toString(),input.operationId());if(!rows.isEmpty()){String old=jdbc.queryForObject("select request_hash from forecast_request where user_id=? and operation_id=?",String.class,user.toString(),input.operationId());if(!hash.equals(old))throw error("PREDICTION_REQUEST_CONFLICT",HttpStatus.CONFLICT);return new Claim(rows.get(0),false);}if(jdbc.queryForObject("select count(*) from forecast_request where user_id=? and state='RUNNING'",Integer.class,user.toString())>0)throw error("PREDICTION_BUSY",HttpStatus.CONFLICT);var v=model.get();var id=UUID.randomUUID();var now=clock.instant();jdbc.update("insert into forecast_request(prediction_id,user_id,operation_id,request_hash,model_id,instrument_id,horizon,state,model_json,created_at,expires_at) values(?,?,?,?,?,?,?,'RUNNING',?,?,?)",id.toString(),user.toString(),input.operationId(),hash,v.modelId().toString(),input.instrumentId(),input.horizon(),json(v),Timestamp.from(now),Timestamp.from(now.plusSeconds(180)));return new Claim(find(user,id),true);});}
 public Record get(UUID user,UUID id){return tx.execute(s->{guard(user);return find(user,id);});}
 private Record find(UUID user,UUID id){var rows=jdbc.query("select * from forecast_request where user_id=? and prediction_id=?",(r,n)->record(r),user.toString(),id.toString());if(rows.isEmpty())throw error("RESOURCE_NOT_FOUND",HttpStatus.NOT_FOUND);return rows.get(0);}
 List<Record> fusionSources(UUID user,List<UUID> ids) {
  var placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
  var parameters=new ArrayList<String>();
  parameters.add(user.toString());
  ids.forEach(id->parameters.add(id.toString()));
  return jdbc.query("select * from forecast_request where user_id=? and hidden=false and prediction_id in ("+placeholders+")",
      (row,index)->record(row),parameters.toArray());
 }
 public Page list(UUID user,int page){if(page<1||page>10000)throw error("PREDICTION_REQUEST_INVALID",HttpStatus.BAD_REQUEST);return tx.execute(s->{guard(user);long total=jdbc.queryForObject("select count(*) from forecast_request where user_id=? and hidden=false",Long.class,user.toString());return new Page(jdbc.query("select * from forecast_request where user_id=? and hidden=false order by created_at desc,prediction_id desc limit 20 offset ?",(r,n)->record(r),user.toString(),(page-1)*20),total,page,20);});}
 public record Latest(String instrumentId,UUID predictionId,String state,int horizon,Instant createdAt) {}
 public List<Latest> latestByInstrument(UUID owner,List<String> input) {
  if(input==null||input.isEmpty()||input.size()>20||input.stream().anyMatch(id->id==null||!id.matches("(SH|SZ)[0-9]{6}")))
      throw error("PREDICTION_REQUEST_INVALID",HttpStatus.BAD_REQUEST);
  var ids=List.copyOf(new LinkedHashSet<>(input));
  var parameters=new ArrayList<Object>();
  parameters.add(Timestamp.from(clock.instant()));
  parameters.add(owner.toString());
  parameters.addAll(ids);
  var placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
  return jdbc.query("""
      select instrument_id,prediction_id,effective_state,horizon,created_at from (
        select instrument_id,prediction_id,horizon,created_at,
           case when state='RUNNING' and expires_at<=? then 'FAILED' else state end as effective_state,
           row_number() over(partition by instrument_id order by created_at desc,prediction_id desc) as row_rank
        from forecast_request where user_id=? and hidden=false and instrument_id in (%s)
      ) recent where row_rank=1 order by instrument_id
      """.formatted(placeholders),(row,n)->new Latest(row.getString(1),UUID.fromString(row.getString(2)),
        row.getString(3),row.getInt(4),row.getTimestamp(5).toInstant()),parameters.toArray());
 }
 public Record hide(UUID user,UUID id){return tx.execute(s->{guard(user);find(user,id);jdbc.update("update forecast_request set hidden=true where user_id=? and prediction_id=?",user.toString(),id.toString());return find(user,id);});}
 public boolean succeed(UUID user,UUID id,JsonNode snapshot){return succeed(user,id,1,snapshot);}
 public boolean succeed(UUID user,UUID id,long generation,JsonNode snapshot){return tx.execute(s->{guard(user);checked(user,id,generation);return jdbc.update("update forecast_request set state='SUCCEEDED',snapshot_json=?,error_code=null,finished_at=?,fencing_revision=fencing_revision+1 where user_id=? and prediction_id=? and execution_generation=? and state='RUNNING' and snapshot_json is null and expires_at>?",json(snapshot),Timestamp.from(clock.instant()),user.toString(),id.toString(),generation,Timestamp.from(clock.instant()))==1;});}
 public void fail(UUID user,UUID id,String code){fail(user,id,1,code);}
 public void fail(UUID user,UUID id,long generation,String code){tx.executeWithoutResult(s->{guard(user);checked(user,id,generation);jdbc.update("update forecast_request set state='FAILED',error_code=?,finished_at=?,fencing_revision=fencing_revision+1 where user_id=? and prediction_id=? and execution_generation=? and state='RUNNING'",code,Timestamp.from(clock.instant()),user.toString(),id.toString(),generation);});}
 private Map<String,Object> checked(UUID user,UUID id,long generation){var rows=jdbc.queryForList("select * from forecast_request where user_id=? and prediction_id=? for update",user.toString(),id.toString());if(rows.size()!=1||generation<1||((Number)rows.get(0).get("execution_generation")).longValue()!=generation)throw error("PREDICTION_REQUEST_CONFLICT",HttpStatus.CONFLICT);return rows.get(0);}
 private UUID owner(UUID id){var users=jdbc.queryForList("select user_id from forecast_request where prediction_id=?",String.class,id.toString());if(users.size()!=1)throw error("RESOURCE_NOT_FOUND",HttpStatus.NOT_FOUND);return UUID.fromString(users.get(0));}
 public String marketUseInput(UUID id,long generation){return tx.execute(s->{var user=owner(id);guard(user);var row=checked(user,id,generation);if(!"RUNNING".equals(row.get("state"))||!clock.instant().isBefore(com.aistock.prediction.maas.MaasOperationGate.instant(row.get("expires_at"))))throw com.aistock.prediction.marketuse.MarketUseClient.unavailable();return row.get("instrument_id").toString();});}
 public JsonNode executionTerminal(UUID id,long generation){return tx.execute(s->{var user=owner(id);guard(user);var row=checked(user,id,generation);if(!Set.of("SUCCEEDED","FAILED").contains(row.get("state"))||row.get("finished_at")==null)throw com.aistock.prediction.marketuse.MarketUseClient.unavailable();return mapper.createObjectNode().put("state",row.get("state").toString()).put("terminalAt",com.aistock.prediction.maas.MaasOperationGate.instant(row.get("finished_at")).toString()).put("fencingRevision",row.get("fencing_revision").toString()).put("resultIdentity",id.toString());});}
 private Record record(ResultSet r)throws SQLException{try{String snapshot=r.getString("snapshot_json");return new Record(UUID.fromString(r.getString("prediction_id")),UUID.fromString(r.getString("operation_id")),UUID.fromString(r.getString("model_id")),r.getString("instrument_id"),r.getInt("horizon"),r.getString("state"),r.getString("error_code"),mapper.readTree(r.getString("model_json")),snapshot==null?null:mapper.readTree(snapshot),r.getBoolean("hidden"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("expires_at").toInstant());}catch(java.io.IOException e){throw error("PREDICTION_RECORD_INVALID",HttpStatus.INTERNAL_SERVER_ERROR);}}
 private String json(Object v){try{return mapper.writeValueAsString(v);}catch(java.io.IOException e){throw error("PREDICTION_RECORD_INVALID",HttpStatus.INTERNAL_SERVER_ERROR);}}
 static BusinessException error(String code,HttpStatus status){return new BusinessException(code,switch(code){case "RESOURCE_NOT_FOUND"->"预测记录不存在";case "PREDICTION_BUSY"->"上一条预测仍在处理，请稍后查看";case "PREDICTION_REQUEST_CONFLICT"->"相同请求编号的参数不同，请查看原记录";case "MODEL_NOT_PUBLISHED"->"该模型尚未发布或已下线";case "PREDICTION_REQUEST_INVALID"->"请选择支持的股票、模型和预测期";default->"预测未完成，已有记录保留";},status);}
}
