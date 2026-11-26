package com.aistock.scheduler.security;
import com.aistock.common.*;import com.fasterxml.jackson.databind.ObjectMapper;import jakarta.servlet.*;import jakarta.servlet.http.*;import java.io.IOException;import java.util.*;import org.springframework.http.MediaType;import org.springframework.web.filter.OncePerRequestFilter;
public final class AdminTaskAccessFilter extends OncePerRequestFilter {
 private final SessionIdentityClient client;private final String token;private final ObjectMapper mapper;private final Set<String> origins;
 public AdminTaskAccessFilter(SessionIdentityClient client,String token,ObjectMapper mapper){this(client,token,mapper,Set.of("http://127.0.0.1:15173","http://localhost:15173"));}
 public AdminTaskAccessFilter(SessionIdentityClient client,String token,ObjectMapper mapper,Set<String> origins){this.client=client;this.token=token;this.mapper=mapper;this.origins=Set.copyOf(origins);}
 public static boolean writePath(String path){return com.aistock.common.adminrefresh.AdminMarketRefreshInput.writePath(path)||path.matches("/api/v1/admin/tasks/[^/]{1,64}/(retry|cancel)")||path.matches("/api/v1/admin/tasks/training/[a-f0-9-]{36}/cancel");}
 public static boolean readPath(String path){return com.aistock.common.adminrefresh.AdminMarketRefreshInput.readPath(path)||path.equals("/api/v1/admin/statistics/tasks")||path.equals("/api/v1/admin/tasks")||path.matches("/api/v1/admin/tasks/[^/]{1,64}(/(attempts|action-options|operations))?")||path.matches("/api/v1/admin/tasks/[^/]{1,64}/operations/[^/]{1,64}");}
 public static boolean allowed(String method,String path){return "GET".equals(method)&&readPath(path)||"POST".equals(method)&&writePath(path);}
 private String single(HttpServletRequest r,String name){var values=Collections.list(r.getHeaders(name));return values.size()==1?values.get(0):null;}
 @Override protected boolean shouldNotFilter(HttpServletRequest r){return !(r.getRequestURI().startsWith("/api/v1/admin/market-data/")||r.getRequestURI().equals("/api/v1/admin/statistics")||r.getRequestURI().startsWith("/api/v1/admin/statistics/")||r.getRequestURI().equals("/api/v1/admin/tasks")||r.getRequestURI().startsWith("/api/v1/admin/tasks/"));}
 @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain)throws IOException,ServletException {
  s.setHeader("Cache-Control","no-store");
  if(!allowed(r.getMethod(),r.getRequestURI())){error(r,s,404,"RESOURCE_NOT_FOUND","接口不存在");return;}
  if("POST".equals(r.getMethod())) {
   var origin=single(r,"Origin");
   if(origin==null||!origins.contains(origin)||!"1".equals(single(r,"X-Aistock-Admin-Action"))){error(r,s,403,"TASK_ACTION_ORIGIN_DENIED","当前来源不允许提交任务操作");return;}
   try{var type=MediaType.parseMediaType(Objects.requireNonNull(single(r,"Content-Type")));if(!"application".equals(type.getType())||!"json".equals(type.getSubtype()))throw new IllegalArgumentException();}
   catch(RuntimeException e){error(r,s,400,"TASK_ACTION_INVALID","请使用JSON提交任务操作");return;}
   if(r.getContentLengthLong()>4096){error(r,s,400,"TASK_ACTION_INVALID","任务操作内容过长");return;}
  }
  if((r.getRequestURI().startsWith("/api/v1/admin/statistics/")||r.getRequestURI().startsWith("/api/v1/admin/market-data/"))&&Collections.list(r.getHeaders("Cookie")).size()>1){error(r,s,401,"AUTH_REQUIRED","请先登录");return;}
  String session=null;int count=0;if(r.getCookies()!=null)for(var c:r.getCookies())if("AISTOCK_SESSION".equals(c.getName())){count++;session=c.getValue();}
  if(count!=1||session==null||session.isBlank()||session.length()>4096){error(r,s,401,"AUTH_REQUIRED","请先登录");return;}
  if(token==null||token.isBlank()){unavailable(r,s);return;}
  try{
   var reply=client.resolve(new SessionIdentityClient.ResolveInput(session),token);
   if(reply!=null&&"AUTH_REQUIRED".equals(reply.code())){error(r,s,401,"AUTH_REQUIRED","请重新登录");return;}
   if(reply==null||!"OK".equals(reply.code())||reply.data()==null||reply.data().userId()==null||reply.data().role()==null||reply.data().credentialVersion()<0||!Set.of("ADMIN","USER").contains(reply.data().role())){unavailable(r,s);return;}
   if(!"ADMIN".equals(reply.data().role())){error(r,s,403,"ACCESS_DENIED","仅管理员可以管理任务");return;}
   r.setAttribute(AdminTaskPrincipal.class.getName(),new AdminTaskPrincipal(reply.data().userId(),reply.data().credentialVersion()));
  }catch(feign.FeignException e){if(e.status()==401)error(r,s,401,"AUTH_REQUIRED","请重新登录");else unavailable(r,s);return;}
  catch(RuntimeException e){unavailable(r,s);return;}
  chain.doFilter(r,s);
 }
 private void unavailable(HttpServletRequest r,HttpServletResponse s)throws IOException{error(r,s,503,"USER_SERVICE_UNAVAILABLE","账号服务暂不可用，请稍后重试");}
 private void error(HttpServletRequest r,HttpServletResponse s,int status,String code,String message)throws IOException{s.setStatus(status);s.setContentType("application/json;charset=UTF-8");mapper.writeValue(s.getOutputStream(),ApiResponse.failure(code,message,TraceIds.normalize(r.getHeader("X-Trace-Id"))));}
}
