package com.aistock.prediction.forecast;
import com.aistock.prediction.modelcatalog.ModelRepository;import com.fasterxml.jackson.databind.ObjectMapper;import java.time.Clock;
import org.springframework.context.annotation.*;import org.springframework.beans.factory.annotation.Value;import org.springframework.jdbc.core.JdbcTemplate;
@Configuration public class ForecastConfiguration {
 @Bean ForecastRepository forecasts(JdbcTemplate jdbc,ObjectMapper mapper,Clock clock){return new ForecastRepository(jdbc,mapper,clock);}
 @Bean ForecastDependencies forecastDependencies(@Value("${aistock.forecast.market-url:http://127.0.0.1:18082}")String market,@Value("${aistock.algorithm.url:}")String algorithm,@Value("${aistock.internal.token:}")String token,ObjectMapper mapper,ForecastRepository forecasts){
  return new HttpForecastDependencies(market,algorithm,token,mapper,operation->{
   if(com.aistock.prediction.marketuse.MarketUseContext.expectedRaw()!=null)forecasts.marketUseInput(operation,1);
  });
 }
 @Bean ForecastService forecastService(ForecastRepository forecasts,ModelRepository models,ForecastDependencies dependencies,ObjectMapper mapper,Clock clock,com.aistock.prediction.marketuse.PredictionMarketUseOwner uses){return new ForecastService(forecasts,models,dependencies,mapper,clock,uses);}
}
