package com.aistock.market.security;
import com.aistock.common.*;import com.fasterxml.jackson.databind.ObjectMapper;import jakarta.servlet.*;import jakarta.servlet.http.*;import java.io.IOException;import org.springframework.web.filter.OncePerRequestFilter;
public class MarketAccessFilter extends OncePerRequestFilter {
 private final SessionIdentityClient client;private final String serviceToken;private final ObjectMapper mapper;
 public MarketAccessFilter(SessionIdentityClient client,String serviceToken,ObjectMapper mapper){this.client=client;this.serviceToken=serviceToken;this.mapper=mapper;}
 @Override protected boolean shouldNotFilter(HttpServletRequest r){return !r.getRequestURI().startsWith("/api/v1/instruments");}
 @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain)throws IOException,ServletException {
  if(!"GET".equals(r.getMethod())){error(r,s,404,"RESOURCE_NOT_FOUND","接口不存在");return;}String token=null;if(r.getCookies()!=null)for(var c:r.getCookies())if("AISTOCK_SESSION".equals(c.getName()))token=c.getValue();
  if(token==null||token.isBlank()){error(r,s,401,"AUTH_REQUIRED","请先登录");return;}
  if(serviceToken.isBlank()){error(r,s,503,"USER_SERVICE_UNAVAILABLE","账号服务认证未配置");return;}
  try{var identity=client.resolve(new SessionIdentityClient.ResolveInput(token),serviceToken);if(identity==null||!"OK".equals(identity.code())||identity.data()==null){error(r,s,401,"AUTH_REQUIRED","请重新登录");return;}}
  catch(feign.FeignException e){if(e.status()==401){error(r,s,401,"AUTH_REQUIRED","请重新登录");return;}error(r,s,503,"USER_SERVICE_UNAVAILABLE","账号服务暂不可用");return;}
  catch(RuntimeException e){error(r,s,503,"USER_SERVICE_UNAVAILABLE","账号服务暂不可用");return;}
  s.setHeader("Cache-Control","no-store");chain.doFilter(r,s);
 }
 private void error(HttpServletRequest r,HttpServletResponse s,int status,String code,String message)throws IOException{s.setStatus(status);s.setContentType("application/json;charset=UTF-8");s.setHeader("Cache-Control","no-store");mapper.writeValue(s.getOutputStream(),ApiResponse.failure(code,message,TraceIds.normalize(r.getHeader("X-Trace-Id"))));}
}
