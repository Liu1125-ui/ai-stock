package com.aistock.prediction.portfolioquestion;
import com.aistock.prediction.portfolioanalysis.PortfolioRepository;import com.aistock.prediction.explanation.MaasClient;import com.fasterxml.jackson.databind.ObjectMapper;import org.springframework.context.annotation.*;import org.springframework.beans.factory.annotation.Value;import org.springframework.jdbc.core.JdbcTemplate;import java.time.Clock;
@Configuration public class PortfolioQuestionConfiguration {
 @Bean PortfolioQuestionRepository portfolioQuestionRecords(JdbcTemplate jdbc,ObjectMapper mapper){return new PortfolioQuestionRepository(jdbc,mapper);}
 @Bean PortfolioQuestionService portfolioQuestionService(PortfolioQuestionRepository repository,PortfolioRepository source,MaasClient client,ObjectMapper mapper,Clock clock,@Value("${aistock.portfolio-question.enabled:false}")boolean enabled,@Value("${aistock.portfolio-question.max-facts:1800}")int maxFacts,@Value("${aistock.portfolio-question.max-fact-bytes:98304}")int maxBytes,@Value("${aistock.portfolio-question.max-prompt-chars:65536}")int maxPrompt){return new PortfolioQuestionService(repository,source,client,mapper,clock,enabled,maxFacts,maxBytes,maxPrompt);}
}
