package com.aistock.gateway.statistics;
import java.time.*;import java.lang.management.ManagementFactory;import org.springframework.context.annotation.*;import org.springframework.beans.factory.annotation.Value;import org.springframework.boot.web.servlet.FilterRegistrationBean;
@Configuration public class HttpStatisticsConfiguration {
 @Bean Clock statisticsClock(){return Clock.systemUTC();}
 @Bean HttpStatisticsCollector httpStatisticsCollector(Clock clock){return new HttpStatisticsCollector(Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime()),clock);}
 @Bean StatisticsAdminAccess statisticsAdminAccess(StatisticsIdentityClient identity,@Value("${SERVICE_TOKEN:}")String token){return new StatisticsAdminAccess(identity,token);}
 @Bean FilterRegistrationBean<HttpStatisticsFilter> httpStatisticsFilter(HttpStatisticsCollector collector){var b=new FilterRegistrationBean<>(new HttpStatisticsFilter(collector,System::nanoTime));b.setOrder(-50);b.addUrlPatterns("/api/v1/*");return b;}
}
