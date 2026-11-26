package com.aistock.prediction.portfolioquestion;
import com.aistock.prediction.portfolioanalysis.PortfolioRepository;import com.fasterxml.jackson.databind.*;import java.util.*;
public final class PortfolioRuleService {
 private final PortfolioRepository source;private final ObjectMapper mapper;
 public PortfolioRuleService(PortfolioRepository source,ObjectMapper mapper){this.source=source;this.mapper=mapper;}
 public JsonNode describe(UUID owner,UUID analysis,String topic){String fact=switch(topic==null?"":topic){case "PROFIT_FORMULA"->"PROFIT_RULE";case "RETURN_LIMIT"->"RETURN_RULE";case "COVERAGE_LIMIT"->"COVERAGE_LIMIT";case "CONCENTRATION_RULE"->"CONCENTRATION_RULE";default->throw PortfolioQuestionInput.invalid();};var saved=source.get(owner,analysis);var f=PortfolioQuestionFacts.inspect(saved);var n=mapper.createObjectNode().put("source","RULE").put("ruleVersion","portfolio-rules-v1").put("analysisId",analysis.toString()).put("topic",topic).put("asOfDate",saved.path("asOfDate").asText()).put("text",f.get(fact));var limits=n.putArray("limits");for(String k:List.of("COVERAGE_LIMIT","DATE_LIMIT","FRESHNESS_LIMIT","BASIS_LIMIT"))limits.add(f.get(k));var refs=n.putObject("facts");for(String k:List.of(fact,"AS_OF"))refs.put(k,f.get(k));return n;}
}
