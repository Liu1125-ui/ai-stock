package com.aistock.prediction.analysis;

import com.aistock.prediction.explanation.ExplanationFacts;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import java.security.MessageDigest;

/** Joins separate historical and predictive evidence without inventing actual returns. */
public final class AnalysisPredictionFacts {
 public static final String PROMPT="grounded-stock-analysis-prediction-v2";
 private AnalysisPredictionFacts(){}
 public static AnalysisFacts.Facts combine(AnalysisFacts.Facts market,UUID id,AnalysisPredictionSource.Reference reference,String provider,ObjectMapper mapper){try{
  var record=reference.record();if(!id.equals(record.predictionId())||!record.instrumentId().equals(market.context().path("instrumentId").asText()))throw AnalysisFacts.invalid();
  AnalysisPredictionSource.validateSnapshot(record.snapshot(),record.modelSnapshot());
  var prediction=ExplanationFacts.build(id,record.snapshot(),record.modelSnapshot(),reference.verification(),provider,mapper);
  var values=new TreeMap<String,String>(market.values());prediction.values().forEach((key,value)->values.put("PRED_"+key,value));
  var verification=reference.verification();if(verification!=null&&!verification.isNull()&&verification.path("result").path("verifiedCount").asInt(-1)==0)values.put("PRED_VERIFICATION",prediction.values().get("VERIFICATION")+"；尚无有效实际样本，不能评价预测准确性");
  values.put("PRED_CONTEXT_RELATION","历史行情截至"+market.values().get("AS_OF")+"；预测原点为"+prediction.context().path("originDate").asText()+"，两类依据分别保存，未复权历史价格与统一原点预测参考价格不拼接为实际收益");
  ObjectNode context=market.context().deepCopy();context.put("promptVersion",PROMPT);context.set("prediction",prediction.context());context.set("facts",mapper.valueToTree(values));
  String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(context)));
  return new AnalysisFacts.Facts(Collections.unmodifiableMap(values),context,hash);
 }catch(com.aistock.common.BusinessException e){throw e;}catch(Exception e){throw AnalysisFacts.invalid();}}
}
