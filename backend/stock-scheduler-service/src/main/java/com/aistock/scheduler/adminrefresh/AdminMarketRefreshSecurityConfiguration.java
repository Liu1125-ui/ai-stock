package com.aistock.scheduler.adminrefresh;
import java.util.*;import org.springframework.context.annotation.*;import org.springframework.beans.factory.annotation.Value;import org.springframework.boot.web.servlet.FilterRegistrationBean;import org.springframework.web.cors.*;import org.springframework.web.filter.CorsFilter;import com.aistock.common.adminrefresh.AdminMarketRefreshInput;
@Configuration public class AdminMarketRefreshSecurityConfiguration {
 @Bean public FilterRegistrationBean<CorsFilter> adminMarketRefreshCors(@Value("${aistock.auth.allowed-origins:http://127.0.0.1:15173,http://localhost:15173}")String origins){
  var read=new CorsConfiguration();read.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::strip).filter(s->!s.isEmpty()).toList());read.setAllowCredentials(true);read.setAllowedMethods(List.of("GET","OPTIONS"));read.setAllowedHeaders(List.of("Content-Type","X-Trace-Id"));
  var write=new CorsConfiguration(read);write.setAllowedMethods(List.of("GET","POST","OPTIONS"));write.setAllowedHeaders(List.of("Content-Type","X-Trace-Id","X-Aistock-Admin-Action"));
  var source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/api/v1/admin/market-data/*/refresh-options",read);source.registerCorsConfiguration("/api/v1/admin/market-data/*/refresh-operations",write);source.registerCorsConfiguration("/api/v1/admin/market-data/refresh-operations/*",read);
  var filter=new CorsFilter(source){@Override protected boolean shouldNotFilter(jakarta.servlet.http.HttpServletRequest r){return !AdminMarketRefreshInput.readPath(r.getRequestURI());}@Override protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest r,jakarta.servlet.http.HttpServletResponse s,jakarta.servlet.FilterChain c)throws java.io.IOException,jakarta.servlet.ServletException{s.setHeader("Cache-Control","no-store");super.doFilterInternal(r,s,c);}};
  var bean=new FilterRegistrationBean<CorsFilter>(filter);bean.setOrder(-110);bean.addUrlPatterns("/api/v1/admin/market-data/*");return bean;
 }
}
