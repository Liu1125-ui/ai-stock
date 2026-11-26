package com.aistock.common.marketrefs;
import com.aistock.common.*;import com.fasterxml.jackson.databind.ObjectMapper;import jakarta.servlet.*;import jakarta.servlet.http.*;import java.io.IOException;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.util.*;import org.springframework.web.filter.OncePerRequestFilter;
public final class ReferenceTokenFilter extends OncePerRequestFilter {
 private final String token;private final ObjectMapper mapper;
 public ReferenceTokenFilter(String token,ObjectMapper mapper){this.token=token;this.mapper=mapper;}
 @Override protected boolean shouldNotFilter(HttpServletRequest r){return !(r.getRequestURI().equals(MarketReferenceInput.INTERNAL_BASE)||r.getRequestURI().startsWith(MarketReferenceInput.INTERNAL_BASE+"/"));}
 @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain)throws IOException,ServletException{
  s.setHeader("Cache-Control","no-store");var values=Collections.list(r.getHeaders("X-Service-Token"));String supplied=values.size()==1?values.get(0):null;
  if(token==null||token.isBlank()){error(r,s,503,"INTERNAL_AUTH_NOT_CONFIGURED");return;}
  if(supplied==null||supplied.length()>512||!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))){error(r,s,401,"INTERNAL_AUTH_REQUIRED");return;}
  if(!"GET".equals(r.getMethod())||!r.getRequestURI().matches(MarketReferenceInput.INTERNAL_BASE+"/[^/]{1,64}")){error(r,s,404,"RESOURCE_NOT_FOUND");return;}
  if(!MarketReferenceInput.internalPath(r.getRequestURI())||!r.getParameterMap().isEmpty()||r.getInputStream().read()!=-1){error(r,s,400,"MARKET_ADMIN_QUERY_INVALID");return;}
  chain.doFilter(r,s);
 }
 private void error(HttpServletRequest r,HttpServletResponse s,int status,String code)throws IOException{s.setStatus(status);s.setContentType("application/json;charset=UTF-8");mapper.writeValue(s.getOutputStream(),ApiResponse.failure(code,"引用查询未通过核验",TraceIds.normalize(r.getHeader("X-Trace-Id"))));}
}
