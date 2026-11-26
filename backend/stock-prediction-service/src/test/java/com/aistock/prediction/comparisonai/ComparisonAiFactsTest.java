package com.aistock.prediction.comparisonai;
import com.fasterxml.jackson.databind.node.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import static com.aistock.prediction.comparisonai.ComparisonAiSamples.*;
class ComparisonAiFactsTest {
 @Test void usesFixedMetricValuesAndStableStockOrder(){var source=saved();var a=ComparisonAiFacts.build(source,"model",M);assertTrue(a.values().get("S1_RETURN").contains("-1.000000"));assertTrue(a.values().get("ACTUAL_RANGE").contains("2026-10-07"));var array=source.withArray("stocks");var first=array.remove(0);array.add(first);assertEquals(a.hash(),ComparisonAiFacts.build(source,"model",M).hash());assertNotEquals(a.hash(),ComparisonAiFacts.build(source,"another",M).hash());assertThrows(UnsupportedOperationException.class,()->a.values().put("X","bad"));}
 @Test void distinguishesMissingSuspendedAndActualZero(){var f=ComparisonAiFacts.build(missing(),"model",M);assertTrue(f.values().get("S1_VOLUME").contains("不可计算"));assertTrue(f.values().get("DATA_LIMIT").contains("数据缺失"));assertTrue(f.values().get("DATA_LIMIT").contains("停牌"));assertTrue(f.values().get("VOL_RELATIONS").contains("不可比较"));var z=ComparisonAiFacts.build(zero(),"model",M);assertTrue(z.values().get("S1_VOLUME").contains("0.0000股"));assertTrue(z.values().get("S1_VOLATILITY").contains("0.000000%"));}
 @Test void rejectsMalformedSourceAndNeverInventsEndpoints(){var bad=saved();bad.put("basis","RAW");assertThrows(RuntimeException.class,()->ComparisonAiFacts.build(bad,"model",M));var source=saved();source.putNull("actualRange");for(var stock:source.withArray("stocks"))for(String key:new String[]{"intervalReturn","annualizedVolatility","maxDrawdown","meanVolume"}){var n=(ObjectNode)stock.path(key);n.put("status","UNAVAILABLE").put("reason","NO_COMMON_ENDPOINTS").putNull("value");}assertTrue(ComparisonAiFacts.build(source,"model",M).values().get("ACTUAL_RANGE").contains("不足"));}
 @Test void thirdStockPromptUsesOnlyKnownPlaceholders(){var s=saved();var third=s.withArray("stocks").get(0).deepCopy();((ObjectNode)third).put("instrumentId","SZ000002");s.withArray("stocks").add(third);var f=ComparisonAiFacts.build(s,"model",M);assertTrue(f.values().containsKey("S3_RETURN"));assertTrue(ComparisonAiPrompt.buildPrompt(f.values()).contains("S3_RETURN"));assertFalse(ComparisonAiPrompt.buildPrompt(ComparisonAiFacts.build(saved(),"model",M).values()).contains("S3_RETURN"));}
 @Test void freshnessUsesTheCommonEffectiveEndInsteadOfTheRequestedEnd(){
  var source=saved();
  ((ObjectNode)source.path("requestedRange")).put("to","2026-10-12");
  ((ObjectNode)source.path("actualRange")).put("to","2026-10-09");
  var facts=ComparisonAiFacts.build(source,"model",M).values();
  assertTrue(facts.get("REQUEST_RANGE").contains("2026-10-12"));
  assertTrue(facts.get("FRESHNESS_LIMIT").contains("共同有效区间截止日为2026-10-09"));
 }
}
