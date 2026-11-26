package com.aistock.market.comparison;
import com.aistock.market.version.MarketVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
@Configuration
public class ComparisonRefreshConfiguration {
 @Bean ComparisonCollector comparisonCollector(@Value("${aistock.algorithm.url:http://127.0.0.1:18090}")String url,@Value("${aistock.internal.token:}")String token,ObjectMapper mapper){
  return new ComparisonCollectorClient(url,token,mapper);
 }
 @Bean ComparisonRefreshRepository comparisonRefreshRepository(MarketVersionRepository market,ObjectMapper mapper,Clock clock){
  return new ComparisonRefreshRepository(market.jdbc(),new ComparisonEvidenceRepository(market,mapper,clock),new ComparisonEvidenceValidator(clock),mapper,clock);
 }
 @Bean ComparisonRefreshService comparisonRefreshService(ComparisonRefreshRepository records,ComparisonUpdateQuery query,ComparisonCollector collector,Clock clock,com.aistock.market.marketlifecycle.MarketComparisonUseOwner uses){
  return new ComparisonRefreshService(records,query,collector,clock,uses);
 }
}
