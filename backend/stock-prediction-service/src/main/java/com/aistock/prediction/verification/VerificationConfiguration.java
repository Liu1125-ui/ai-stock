package com.aistock.prediction.verification;
import com.fasterxml.jackson.databind.ObjectMapper;import java.time.Clock;import org.springframework.context.annotation.*;import org.springframework.beans.factory.annotation.Value;import org.springframework.jdbc.core.JdbcTemplate;
@Configuration public class VerificationConfiguration {
 @Bean VerificationDueRepository verificationDueRepository(JdbcTemplate jdbc,ObjectMapper mapper,Clock clock,@Value("${aistock.market.update-hour:20}")int hour){return new VerificationDueRepository(jdbc,mapper,clock,hour);}
 @Bean VerificationQueryRepository verificationQueryRepository(JdbcTemplate jdbc,ObjectMapper mapper,Clock clock,@Value("${aistock.market.update-hour:20}")int hour){return new VerificationQueryRepository(jdbc,mapper,clock,hour);}
 @Bean VerificationRepository verificationRepository(JdbcTemplate jdbc,ObjectMapper mapper,Clock clock){return new VerificationRepository(jdbc,mapper,clock);}
 @Bean VerificationMarketClient verificationMarketClient(@Value("${aistock.forecast.market-url:http://127.0.0.1:18082}")String url,@Value("${aistock.internal.token:}")String token,ObjectMapper mapper){return new HttpVerificationMarketClient(url,token,mapper);}
 @Bean VerificationService verificationService(VerificationRepository repo,VerificationMarketClient market,ObjectMapper mapper,Clock clock,@Value("${aistock.market.update-hour:20}")int hour,com.aistock.prediction.marketuse.PredictionMarketUseOwner uses){return new VerificationService(repo,market,new VerificationCalculator(),mapper,clock,hour,uses);}
}
