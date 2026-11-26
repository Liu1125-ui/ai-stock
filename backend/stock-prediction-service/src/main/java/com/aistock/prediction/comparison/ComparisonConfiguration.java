package com.aistock.prediction.comparison;
import org.springframework.context.annotation.*;import org.springframework.beans.factory.annotation.Value;import org.springframework.jdbc.core.JdbcTemplate;import com.fasterxml.jackson.databind.ObjectMapper;import java.time.Clock;
@Configuration public class ComparisonConfiguration {
 @Bean ComparisonRepository comparisonRepository(JdbcTemplate jdbc,ObjectMapper mapper){return new ComparisonRepository(jdbc,mapper);}
 @Bean ComparisonMarketClient comparisonMarket(@Value("${aistock.forecast.market-url:}")String url,@Value("${aistock.internal.token:}")String token,ObjectMapper mapper){return new HttpComparisonMarketClient(url,token,mapper);}
 @Bean ComparisonService comparisons(ComparisonRepository repository,ComparisonMarketClient market,ObjectMapper mapper,Clock clock,@Value("${aistock.comparison.max-concurrent:4}")int limit,com.aistock.prediction.marketuse.ComparisonMarketUseOwner uses){return new ComparisonService(repository,market,mapper,clock,limit,uses);}
}
