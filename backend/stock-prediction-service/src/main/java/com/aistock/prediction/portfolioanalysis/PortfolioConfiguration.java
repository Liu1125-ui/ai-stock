package com.aistock.prediction.portfolioanalysis;
import com.aistock.prediction.MarketDependencyGuard;import com.fasterxml.jackson.databind.ObjectMapper;import org.springframework.context.annotation.*;import org.springframework.beans.factory.annotation.Value;import org.springframework.jdbc.core.JdbcTemplate;import java.time.Clock;import java.math.BigDecimal;
@Configuration public class PortfolioConfiguration {
 @Bean PortfolioRepository portfolioRepository(JdbcTemplate jdbc,ObjectMapper mapper){return new PortfolioRepository(jdbc,mapper);}
 @Bean PortfolioDependencyHttp portfolioHttp(@Value("${aistock.internal.token:}")String token,ObjectMapper mapper){return new PortfolioDependencyHttp(token,mapper);}
 @Bean PortfolioSources portfolioSources(@Value("${aistock.portfolio.user-url:http://127.0.0.1:18081}")String user,@Value("${aistock.portfolio.market-url:http://127.0.0.1:18082}")String market,PortfolioDependencyHttp http,ObjectMapper mapper,MarketDependencyGuard guard){return new PortfolioSources(user,market,http,mapper,guard);}
 @Bean PortfolioService portfolioService(PortfolioRepository repository,PortfolioSources sources,Clock clock,com.aistock.prediction.marketuse.PortfolioMarketUseOwner uses,@Value("${aistock.portfolio.concentration-threshold-percent:50}")BigDecimal threshold,@Value("${aistock.portfolio.max-concurrent:2}")int concurrent){return new PortfolioService(repository,sources,clock,threshold,concurrent,uses);}
}
