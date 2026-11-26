package com.aistock.market.statistics;
import com.aistock.common.*;import com.aistock.market.security.SessionIdentityClient;import com.fasterxml.jackson.databind.ObjectMapper;import jakarta.servlet.*;import jakarta.servlet.http.*;import java.io.IOException;import java.util.*;import org.springframework.web.filter.OncePerRequestFilter;
public final class AdminStatisticsAccessFilter extends OncePerRequestFilter {
 public static final String ADMIN=AdminStatisticsAccessFilter.class.getName()+".admin";
 private final SessionIdentityClient client;private final String token;private final ObjectMapper mapper;
 public AdminStatisticsAccessFilter(SessionIdentityClient client,String token,ObjectMapper mapper){this.client=client;this.token=token;this.mapper=mapper;}
 @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain)throws IOException,ServletException{
  s.setHeader("Cache-Control","no-store");if(!"GET".equals(r.getMethod())||!r.getRequestURI().equals("/api/v1/admin/statistics/market")){error(r,s,404,"RESOURCE_NOT_FOUND","接口不存在");return;}
  int count=0;String session=null;if(r.getCookies()!=null)for(var c:r.getCookies())if("AISTOCK_SESSION".equals(c.getName())){count++;session=c.getValue();}
  if(count!=1||session==null||session.isBlank()||session.length()>4096||Collections.list(r.getHeaders("Cookie")).size()>1){error(r,s,401,"AUTH_REQUIRED","请先登录");return;}
  if(token==null||token.isBlank()){unavailable(r,s);return;}
  try{var reply=client.resolve(new SessionIdentityClient.ResolveInput(session),token);if(reply!=null&&"AUTH_REQUIRED".equals(reply.code())){error(r,s,401,"AUTH_REQUIRED","请重新登录");return;}
   if(reply==null||!"OK".equals(reply.code())||reply.data()==null||reply.data().userId()==null||!reply.data().userId().matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}")||reply.data().credentialVersion()<0||reply.data().role()==null||!Set.of("ADMIN","USER").contains(reply.data().role())){unavailable(r,s);return;}
   if(!"ADMIN".equals(reply.data().role())){error(r,s,403,"ACCESS_DENIED","仅管理员可以查看统计");return;}
  }catch(feign.FeignException e){if(e.status()==401)error(r,s,401,"AUTH_REQUIRED","请重新登录");else unavailable(r,s);return;}catch(RuntimeException e){unavailable(r,s);return;}
  r.setAttribute(ADMIN,Boolean.TRUE);chain.doFilter(r,s);
 }
 private void unavailable(HttpServletRequest r,HttpServletResponse s)throws IOException{error(r,s,503,"USER_SERVICE_UNAVAILABLE","账号服务暂不可用");}
 private void error(HttpServletRequest r,HttpServletResponse s,int status,String code,String message)throws IOException{s.setStatus(status);s.setContentType("application/json;charset=UTF-8");mapper.writeValue(s.getOutputStream(),ApiResponse.failure(code,message,TraceIds.normalize(r.getHeader("X-Trace-Id"))));}
}
