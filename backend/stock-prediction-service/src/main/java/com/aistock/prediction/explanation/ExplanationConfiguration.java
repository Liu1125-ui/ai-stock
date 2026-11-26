package com.aistock.prediction.explanation;
import com.aistock.prediction.forecast.ForecastRepository;import com.aistock.prediction.verification.VerificationQueryRepository;import com.fasterxml.jackson.databind.ObjectMapper;import java.time.Clock;import org.springframework.context.annotation.*;import org.springframework.beans.factory.annotation.Value;import org.springframework.jdbc.core.JdbcTemplate;
@Configuration public class ExplanationConfiguration {
 @Bean ExplanationRepository explanations(JdbcTemplate jdbc,ObjectMapper mapper){return new ExplanationRepository(jdbc,mapper);}
 @Bean MaasClient maasClient(@Value("${aistock.maas.chat-url:}")String url,@Value("${aistock.maas.model:}")String model,@Value("${aistock.maas.api-key:}")String key,@Value("${aistock.maas.enabled:false}")boolean enabled,ObjectMapper mapper){return new HttpMaasClient(url,model,key,enabled,mapper);}
 @Bean ExplanationService explanationService(ForecastRepository forecasts,VerificationQueryRepository verifications,ExplanationRepository repository,MaasClient client,ObjectMapper mapper,Clock clock){return new ExplanationService(forecasts,verifications,repository,client,mapper,clock);}
}
