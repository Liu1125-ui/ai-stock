package com.aistock.prediction.question;
import com.aistock.prediction.analysis.AnalysisRepository;import com.aistock.prediction.explanation.MaasClient;import com.fasterxml.jackson.databind.ObjectMapper;import java.time.Clock;import org.springframework.context.annotation.*;import org.springframework.jdbc.core.JdbcTemplate;
@Configuration public class QuestionConfiguration {
 @Bean QuestionRepository questionRecords(JdbcTemplate j,ObjectMapper m){return new QuestionRepository(j,m);}
 @Bean QuestionService questionService(QuestionRepository r,AnalysisRepository a,MaasClient c,ObjectMapper m,Clock clock){return new QuestionService(r,a,c,m,clock);}
}
