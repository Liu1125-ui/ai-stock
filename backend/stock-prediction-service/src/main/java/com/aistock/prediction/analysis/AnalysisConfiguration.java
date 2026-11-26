package com.aistock.prediction.analysis;
import com.aistock.prediction.explanation.MaasClient;import com.fasterxml.jackson.databind.ObjectMapper;import java.time.Clock;import org.springframework.context.annotation.*;import org.springframework.beans.factory.annotation.Value;import org.springframework.jdbc.core.JdbcTemplate;
@Configuration public class AnalysisConfiguration {
 @Bean AnalysisRepository analysisRecords(JdbcTemplate jdbc,ObjectMapper m){return new AnalysisRepository(jdbc,m);}
 @Bean AnalysisMarketClient analysisMarket(@Value("${aistock.forecast.market-url:}")String base,@Value("${aistock.internal.token:}")String token,ObjectMapper m){return new HttpAnalysisMarketClient(base,token,m);}
 @Bean AnalysisPredictionSource analysisPredictionSource(JdbcTemplate jdbc,ObjectMapper m,com.aistock.prediction.verification.VerificationQueryRepository verifications){return new AnalysisPredictionSource(jdbc,m,verifications);}
 @Bean AnalysisService analysisService(AnalysisRepository r,AnalysisMarketClient market,MaasClient client,ObjectMapper m,Clock clock,AnalysisPredictionSource source,com.aistock.prediction.marketuse.MarketUseOwnerService uses){return new AnalysisService(r,market,client,m,clock,source,uses);}
}