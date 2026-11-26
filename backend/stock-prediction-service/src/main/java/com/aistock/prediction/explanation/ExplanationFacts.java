package com.aistock.prediction.explanation;
import com.fasterxml.jackson.databind.*;import com.fasterxml.jackson.databind.node.ObjectNode;import java.math.*;import java.time.LocalDate;import java.util.*;import java.security.MessageDigest;
public final class ExplanationFacts {
 public static final String PROMPT="grounded-prediction-v3";
 static boolean supportsSavedPrompt(String version){
  return PROMPT.equals(version)||"grounded-prediction-v2".equals(version);
 }
 public record Facts(ObjectNode context,Map<String,String> values,String hash){}
 public static Facts build(UUID prediction,JsonNode snapshot,JsonNode model,JsonNode verification,String provider,ObjectMapper mapper){try{
  var values=new LinkedHashMap<String,String>();var i=snapshot.path("input");var points=snapshot.path("prediction").path("points");if(!points.isArray()||points.size()!=5)throw invalid();var origin=new BigDecimal(i.path("originRawClose").asText());if(origin.signum()<=0)throw invalid();String date=LocalDate.parse(i.path("originDate").asText()).toString();values.put("ORIGIN_DATE",date);values.put("ORIGIN_CLOSE",origin.toPlainString()+"元（原点实际收盘价）");int up=0,down=0,n=0,rises=0,falls=0;BigDecimal previous=null;for(var p:points){n++;var price=new BigDecimal(p.path("referencePrice").asText());if(price.signum()<=0)throw invalid();if(previous!=null){if(price.compareTo(previous)>0)rises++;if(price.compareTo(previous)<0)falls++;}previous=price;int direction=price.compareTo(origin);if(direction>0)up++;if(direction<0)down++;values.put("R"+n,price.subtract(origin).multiply(BigDecimal.valueOf(100)).divide(origin,6,RoundingMode.HALF_UP).toPlainString()+"%（相对原点的预测变化，非实际收益）");values.put("D"+n,LocalDate.parse(p.path("targetDate").asText()).toString());values.put("P"+n,price.toPlainString()+"元（预测参考价格，非实际行情）");}
  values.put("TREND",rises>0&&falls==0?"上行":falls>0&&rises==0?"下行":rises==0&&falls==0?"持平":"有升有降");values.put("TREND", "预测序列从首个目标日起"+values.get("TREND")+"（只比较相邻预测点，不表示高于或低于原点）");values.put("RELATIVE_ORIGIN",up==5?"均高于原点收盘价":down==5?"均低于原点收盘价":up==0&&down==0?"均等于原点收盘价":"部分高于、部分低于或等于原点收盘价");values.put("MODEL",model.path("artifactId").asText());values.put("TRAINING_CUTOFF",model.path("manifest").path("trainingCutoff").asText());
  values.put("VERIFICATION","尚无有效实际样本，不能评价预测准确性");
  if(verification!=null&&!verification.isNull()){
   var result=verification.path("result");
   int count=result.path("verifiedCount").asInt(-1);
   if(count<0||count>5)throw invalid();
   var cutoff=verification.path("dueThrough").asText();
   String summary="已核验"+count+"个目标，验证证据截止日为"+cutoff;
   values.put("VERIFICATION",summary+(count==0?"。尚无有效实际样本，不能评价预测准确性":"。只统计有实际行情证据的已核验目标"));
   if(count>0){
    var metrics=result.path("metrics");
    var labels=new ArrayList<String>();
    appendMetric(labels,metrics,"priceMae","价格 MAE"," 元",BigDecimal.ONE);
    appendMetric(labels,metrics,"priceRmse","价格 RMSE"," 元",BigDecimal.ONE);
    appendMetric(labels,metrics,"directionAccuracy","方向准确率","%",BigDecimal.valueOf(100));
    if(!labels.isEmpty())values.put("METRICS",String.join("，",labels));
   }
  }
  values.put("LIMIT","预测和历史误差仅供辅助参考，实际表现需等待目标日行情；当前未校准误差区间");
  var root=mapper.createObjectNode();root.put("predictionId",prediction.toString()).put("promptVersion",PROMPT).put("providerModel",provider).put("originDate",date);for(String key:List.of("inputVersionId","rawVersionId","inputHash"))root.put(key,i.path(key).asText());root.put("artifactId",model.path("artifactId").asText()).put("manifestHash",model.path("manifestHash").asText()).put("checkpointHash",model.path("checkpointHash").asText()).put("publicationRevision",snapshot.path("prediction").path("publicationRevision").asInt());if(verification==null||verification.isNull())root.putNull("verificationVersionId");else root.put("verificationVersionId",verification.path("verificationVersionId").asText());root.set("facts",mapper.valueToTree(values));String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(root)));return new Facts(root,Collections.unmodifiableMap(values),hash);
 }catch(Exception e){throw invalid();}}
 private static void appendMetric(List<String> labels,JsonNode metrics,String key,String label,String unit,BigDecimal coefficient){
  var metric=metrics.path(key);
  if(metric.isTextual())labels.add(label+" "+new BigDecimal(metric.asText()).multiply(coefficient).stripTrailingZeros().toPlainString()+unit);
 }
 static com.aistock.common.BusinessException invalid(){return new com.aistock.common.BusinessException("AI_CONTEXT_INVALID","解释依据未通过检查，原预测保留",org.springframework.http.HttpStatus.CONFLICT);}
}
