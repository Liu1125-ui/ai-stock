package com.aistock.prediction.analysis;

import com.aistock.common.BusinessException;
import com.aistock.prediction.forecast.ForecastRecords;
import com.aistock.prediction.verification.VerificationQueryRepository;
import com.fasterxml.jackson.databind.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;

/** Owner-scoped local prediction selection; never trains or invokes a model. */
public final class AnalysisPredictionSource {
 public record Reference(ForecastRecords.Record record, JsonNode verification) {}
 private final JdbcTemplate jdbc; private final ObjectMapper mapper;
 private final VerificationQueryRepository verifications;
 public AnalysisPredictionSource(JdbcTemplate jdbc,ObjectMapper mapper,VerificationQueryRepository verifications){this.jdbc=jdbc;this.mapper=mapper;this.verifications=verifications;}
 private static final String ELIGIBLE="user_id=? and instrument_id=? and state='SUCCEEDED' and hidden=false and horizon=5 and snapshot_json is not null and JSON_LENGTH(JSON_EXTRACT(snapshot_json,'$.prediction.points'))=5";
 public JsonNode options(UUID user,String stock,int page){
  if(stock==null||!stock.matches("(SH|SZ)[0-9]{6}")||page<1||page>10000)throw invalid();
  var root=mapper.createObjectNode().put("page",page).put("size",10);
  root.put("total",jdbc.queryForObject("select count(*) from forecast_request where "+ELIGIBLE,Long.class,user.toString(),stock));
  var items=root.putArray("items");
  jdbc.query("select prediction_id,created_at,model_json,snapshot_json from forecast_request where "+ELIGIBLE+" order by created_at desc,prediction_id desc limit 10 offset ?",rs->{
   var snapshot=parse(rs.getString("snapshot_json"));var model=parse(rs.getString("model_json"));var points=snapshot.path("prediction").path("points");
   items.addObject().put("predictionId",rs.getString("prediction_id")).put("createdAt",rs.getTimestamp("created_at").toInstant().toString()).put("originDate",snapshot.path("input").path("originDate").asText()).put("artifactId",model.path("artifactId").asText()).put("targetFrom",points.get(0).path("targetDate").asText()).put("targetTo",points.get(4).path("targetDate").asText());
  },user.toString(),stock,(page-1)*10);
  return root;
 }
 public Reference read(UUID user,String stock,UUID id){
  var rows=jdbc.queryForList("select * from forecast_request where user_id=? and prediction_id=? and hidden=false",user.toString(),id.toString());
  if(rows.isEmpty())throw new BusinessException("RESOURCE_NOT_FOUND","未找到可引用的预测",HttpStatus.NOT_FOUND);
  var r=rows.get(0);if(!stock.equals(r.get("instrument_id"))||!"SUCCEEDED".equals(r.get("state"))||((Number)r.get("horizon")).intValue()!=5)throw invalid();
  var snap=parse((String)r.get("snapshot_json"));var model=parse((String)r.get("model_json"));
  validateSnapshot(snap,model);
  var record=new ForecastRecords.Record(id,UUID.fromString(r.get("operation_id").toString()),UUID.fromString(r.get("model_id").toString()),stock,5,"SUCCEEDED",null,model,snap,false,((java.sql.Timestamp)r.get("created_at")).toInstant(),((java.sql.Timestamp)r.get("expires_at")).toInstant());
  return new Reference(record,verifications.list(user,id,1).path("current"));
 }
 public static void validateSnapshot(JsonNode snapshot,JsonNode model){try{
  var input=snapshot.path("input");var origin=LocalDate.parse(input.path("originDate").asText());positive(input.path("originRawClose"));
  if(!model.path("artifactId").isTextual()||model.path("artifactId").asText().isBlank())throw invalid();
  var points=snapshot.path("prediction").path("points");if(!points.isArray()||points.size()!=5)throw invalid();
  var previous=origin;int step=0;for(var point:points){if(!point.path("step").isIntegralNumber()||point.path("step").asInt()!=++step)throw invalid();var date=LocalDate.parse(point.path("targetDate").asText());if(!date.isAfter(previous))throw invalid();previous=date;positive(point.path("referencePrice"));}
 }catch(BusinessException e){throw e;}catch(RuntimeException e){throw invalid();}}
 private static void positive(JsonNode n){if(!n.isTextual()||!n.asText().matches("[0-9]{1,16}(\\.[0-9]{1,12})?")||new BigDecimal(n.asText()).signum()<=0)throw invalid();}
 private JsonNode parse(String s){try{return mapper.readTree(s);}catch(Exception e){throw invalid();}}
 private static BusinessException invalid(){return new BusinessException("ANALYSIS_PREDICTION_INVALID","请选择本人同股票的完整成功五日预测",HttpStatus.CONFLICT);}
}
