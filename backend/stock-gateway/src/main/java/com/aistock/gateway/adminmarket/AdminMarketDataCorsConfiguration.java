package com.aistock.gateway.adminmarket;
import com.aistock.gateway.security.AdminReadAccess;
import com.aistock.gateway.statistics.StatisticsIdentityClient;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.cors.*;
import org.springframework.web.filter.CorsFilter;
@Configuration
public class AdminMarketDataCorsConfiguration {
 @Bean AdminReadAccess adminReadAccess(StatisticsIdentityClient identity,@Value("${SERVICE_TOKEN:}")String token){return new AdminReadAccess(identity,token);}
 @Bean public FilterRegistrationBean<CorsFilter> adminMarketDataCors(@Value("${aistock.auth.allowed-origins:http://127.0.0.1:15173,http://localhost:15173}")String origins) {
  var config=new CorsConfiguration();config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::strip).filter(s->!s.isEmpty()).toList());config.setAllowCredentials(true);config.setAllowedMethods(List.of("GET","OPTIONS"));config.setAllowedHeaders(List.of("Content-Type","X-Trace-Id"));
  var source=new UrlBasedCorsConfigurationSource();
  for(String path:List.of("/api/v1/admin/market-data","/api/v1/admin/market-data/*/versions","/api/v1/admin/market-data/*/versions/*","/api/v1/admin/market-data/*/versions/*/references","/api/v1/admin/market-data/*/versions/*/lifecycle","/api/v1/admin/market-data/*/versions/*/lifecycle/history"))source.registerCorsConfiguration(path,config);
  var review=new CorsConfiguration(config);review.setAllowedMethods(List.of("GET","POST","OPTIONS"));review.setAllowedHeaders(List.of("Content-Type","X-Trace-Id","X-Aistock-Admin-Action"));source.registerCorsConfiguration("/api/v1/admin/market-data/*/versions/*/reviews",review);source.registerCorsConfiguration("/api/v1/admin/market-data/*/versions/*/reviews/*",config);
  source.registerCorsConfiguration("/api/v1/admin/market-data/*/versions/*/lifecycle/actions",review);
  source.registerCorsConfiguration("/api/v1/admin/market-data/*/versions/*/lifecycle/actions/*",config);
  var filter=new CorsFilter(source){@Override protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest r,jakarta.servlet.http.HttpServletResponse s,jakarta.servlet.FilterChain chain)throws java.io.IOException,jakarta.servlet.ServletException{s.setHeader("Cache-Control","no-store");super.doFilterInternal(r,s,chain);}};
  var bean=new FilterRegistrationBean<CorsFilter>(filter);bean.setOrder(-100);bean.addUrlPatterns("/api/v1/admin/market-data","/api/v1/admin/market-data/*");return bean;
 }
}
