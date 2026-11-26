package com.aistock.gateway.statistics;
import com.aistock.gateway.ProxyHeaderPolicy;import jakarta.servlet.*;import jakarta.servlet.http.*;import java.io.IOException;import java.util.function.LongSupplier;import org.springframework.web.filter.OncePerRequestFilter;import org.springframework.web.servlet.HandlerMapping;
public final class HttpStatisticsFilter extends OncePerRequestFilter {
 private final HttpStatisticsCollector collector;private final LongSupplier nanoTime;
 public HttpStatisticsFilter(HttpStatisticsCollector collector,LongSupplier nanoTime){this.collector=collector;this.nanoTime=nanoTime;}
 @Override protected boolean shouldNotFilter(HttpServletRequest r){return !r.getRequestURI().startsWith("/api/v1/")||r.getRequestURI().startsWith("/api/v1/admin/statistics")||"OPTIONS".equals(r.getMethod());}
 @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain)throws IOException,ServletException{long start=nanoTime.getAsLong();boolean completed=false;try{chain.doFilter(r,s);completed=true;}finally{
  var pattern=r.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);String group=pattern==null?null:group(pattern.toString());
  if(group!=null&&ProxyHeaderPolicy.allowed(r.getMethod(),r.getRequestURI())){if(completed&&!r.isAsyncStarted())collector.record(group,s.getStatus(),nanoTime.getAsLong()-start);else collector.drop();}
 }}
 private static String group(String p){if(p.startsWith("/api/v1/admin/"))return "ADMIN";if(p.startsWith("/api/v1/auth/")||p.startsWith("/api/v1/users/"))return "USER";if(p.startsWith("/api/v1/instruments"))return "MARKET";if(p.startsWith("/api/v1/predictions")||p.startsWith("/api/v1/models"))return "PREDICTION";if(p.startsWith("/api/v1/stock-"))return "ANALYSIS";if(p.startsWith("/api/v1/comparison"))return "COMPARISON";if(p.startsWith("/api/v1/portfolio-"))return "PORTFOLIO";if(p.startsWith("/api/v1/alerts")||p.startsWith("/api/v1/messages")||p.startsWith("/api/v1/notification-preferences"))return "NOTIFICATION";return null;}
}
