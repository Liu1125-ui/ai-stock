package com.aistock.user.alert;
import com.aistock.common.*;import com.fasterxml.jackson.databind.ObjectMapper;import jakarta.servlet.*;import jakarta.servlet.http.*;import java.io.IOException;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.util.*;import org.springframework.beans.factory.annotation.Value;import org.springframework.core.annotation.Order;import org.springframework.stereotype.Component;import org.springframework.web.filter.OncePerRequestFilter;
@Component @Order(-110) public class AlertAccountTokenFilter extends OncePerRequestFilter {
 private final String token;private final ObjectMapper mapper;
 public AlertAccountTokenFilter(@Value("${aistock.internal.token:}")String token,ObjectMapper mapper){this.token=token;this.mapper=mapper;}
 @Override protected boolean shouldNotFilter(HttpServletRequest request){String path=new org.springframework.web.util.UrlPathHelper().getPathWithinApplication(request);return !path.startsWith("/internal/v1/users");}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  response.setHeader("Cache-Control","no-store");var values=Collections.list(request.getHeaders("X-Service-Token"));String supplied=values.size()==1?values.get(0):null;
  if(token==null||token.isBlank()||supplied==null||supplied.length()>512||!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))){response.setStatus(401);response.setContentType("application/json");response.setCharacterEncoding("UTF-8");mapper.writeValue(response.getWriter(),ApiResponse.failure("INTERNAL_AUTH_REQUIRED","内部服务认证失败",TraceIds.normalize(request.getHeader("X-Trace-Id"))));return;}
  chain.doFilter(request,response);
 }
}
