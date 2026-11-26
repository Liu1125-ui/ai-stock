package com.aistock.market.realization;
import com.aistock.market.version.MarketVersionRepository;import com.fasterxml.jackson.databind.ObjectMapper;import java.time.*;import org.springframework.beans.factory.annotation.Value;import org.springframework.context.annotation.*;
@Configuration public class RealizationConfiguration {
 @Bean RealizationRepository realizationRepository(MarketVersionRepository market,ObjectMapper mapper,Clock clock){return new RealizationRepository(market,mapper,clock);}
 @Bean RealizationCollector realizationCollector(@Value("${aistock.algorithm.url}")String url,@Value("${aistock.internal.token:}")String token){return new RealizationSourceClient(url,token,Duration.ofSeconds(35));}
 @Bean RealizationValidator realizationValidator(Clock clock,@Value("${aistock.market.update-hour:20}")int hour){return new RealizationValidator(clock,hour);}
 @Bean RealizationService realizationService(RealizationRepository repo,RealizationCollector collector,RealizationValidator validator,ObjectMapper mapper,Clock clock){return new RealizationService(repo,collector,validator,mapper,clock);}
}
