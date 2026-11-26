package com.aistock.prediction.comparisonai;
import com.aistock.prediction.comparison.*;
import com.fasterxml.jackson.databind.*;import com.fasterxml.jackson.databind.node.*;
import java.time.*;import java.util.*;
import static com.aistock.prediction.comparison.ComparisonTestEvidence.*;
public final class ComparisonAiSamples {
 public static final ObjectMapper M=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
 public static final UUID ID=UUID.fromString("11111111-1111-4111-8111-111111111111");
 public static ObjectNode saved(){return build(false,false);}
 public static ObjectNode missing(){return build(true,false);}
 public static ObjectNode zero(){return build(false,true);}
 private static ObjectNode build(boolean missing,boolean zero){
  var a=sample("SH600030",zero?new String[]{"10","10","10"}:new String[]{"100","110","99"},zero?new String[]{"0","0","0"}:missing?new String[]{"10",null,"30"}:new String[]{"10","20","30"});
  var b=sample("SH600276",missing?new String[]{"10",null,"12"}:new String[]{"10","11","12"},missing?new String[]{"10",null,"30"}:new String[]{"10","20","30"});
  if(missing){((ObjectNode)b.withArray("days").get(1)).put("priceState","SUSPENDED").put("volumeState","SUSPENDED");rehash(b);}
  var sources=List.<JsonNode>of(a,b);var input=new ComparisonRecords.Input(UUID.randomUUID(),List.of("SH600030","SH600276"),LocalDate.parse("2026-07-09"),LocalDate.parse("2026-10-09"),sources.stream().map(n->UUID.fromString(n.path("evidenceId").asText())).toList());
  var result=(ObjectNode)M.valueToTree(new ComparisonCalculator(Clock.fixed(Instant.parse("2026-10-13T00:00:00Z"),ZoneOffset.UTC)).calculate(input,sources));
  return result.put("comparisonId",ID.toString()).put("createdAt","2026-10-13T00:00:00Z");
 }
}
