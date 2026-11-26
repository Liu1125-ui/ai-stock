package com.aistock.prediction.comparisonai;
import com.aistock.prediction.comparison.ComparisonRepository;import com.aistock.prediction.explanation.MaasClient;import com.fasterxml.jackson.databind.ObjectMapper;import java.time.Clock;import org.springframework.context.annotation.*;import org.springframework.beans.factory.annotation.Value;import org.springframework.jdbc.core.JdbcTemplate;
@Configuration public class ComparisonAiConfiguration {
 @Bean ComparisonAiRepository comparisonAiRecords(JdbcTemplate jdbc,ObjectMapper mapper,Clock clock){return new ComparisonAiRepository(jdbc,mapper,clock);}
 @Bean ComparisonAiService comparisonAiService(ComparisonAiRepository records,ComparisonRepository source,MaasClient client,ObjectMapper mapper,Clock clock,@Value("${aistock.comparison-ai.enabled:false}")boolean enabled){return new ComparisonAiService(records,source,client,mapper,clock,enabled);}
}
