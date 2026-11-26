package com.aistock.notification.alert;
import com.aistock.common.*;import com.fasterxml.jackson.databind.ObjectMapper;import jakarta.servlet.*;import jakarta.servlet.http.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.util.*;import org.springframework.beans.factory.annotation.Value;import org.springframework.core.annotation.Order;import org.springframework.stereotype.Component;import org.springframework.web.filter.OncePerRequestFilter;import org.springframework.http.HttpStatus;
@Component @Order(-100) public class NotificationSecurityFilter extends OncePerRequestFilter {
 public static final String OWNER="notification.owner",SESSION="notification.session";
 private final NotificationAccess access;private final ObjectMapper mapper;private final Set<String> origins;
 public NotificationSecurityFilter(NotificationAccess access,ObjectMapper mapper,@Value("${aistock.web.allowed-origins:http://localhost:15173,http://127.0.0.1:15173}")String origins){this.access=access;this.mapper=mapper;this.origins=Set.of(origins.split(","));}
 @Override protected boolean shouldNotFilter(HttpServletRequest r){String p=new org.springframework.web.util.UrlPathHelper().getPathWithinApplication(r);return !(p.startsWith("/api/v1/alerts")||p.startsWith("/api/v1/notification-preferences")||p.startsWith("/api/v1/messages"));}
 @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain)throws ServletException,IOException {
  s.setHeader("Cache-Control","no-store");
  try{if(Collections.list(r.getHeaders("Origin")).size()>1||Collections.list(r.getHeaders("X-XSRF-TOKEN")).size()>1)throw denied();if(!allowed(r.getMethod(),r.getRequestURI()))throw new BusinessException("RESOURCE_NOT_FOUND","接口不存在",HttpStatus.NOT_FOUND);String origin=r.getHeader("Origin");boolean write=!Set.of("GET","HEAD","OPTIONS").contains(r.getMethod());
   if(origin!=null&&!origins.contains(origin)||write&&origin==null)throw denied();
   if(origin!=null){s.setHeader("Access-Control-Allow-Origin",origin);s.setHeader("Access-Control-Allow-Credentials","true");s.setHeader("Vary","Origin");}
   if("OPTIONS".equals(r.getMethod())){if(origin==null)throw denied();s.setHeader("Access-Control-Allow-Methods","GET,POST,PATCH,OPTIONS");s.setHeader("Access-Control-Allow-Headers","Content-Type,X-XSRF-TOKEN,X-Trace-Id");s.setStatus(204);return;}
   String session=cookie(r,"AISTOCK_SESSION");if(session==null||session.isBlank()||session.length()>512)throw new BusinessException("AUTH_REQUIRED","请先登录",HttpStatus.UNAUTHORIZED);
   if(write){String csrf=cookie(r,"XSRF-TOKEN"),header=r.getHeader("X-XSRF-TOKEN");if(csrf==null||header==null||csrf.isEmpty()||!MessageDigest.isEqual(csrf.getBytes(StandardCharsets.UTF_8),header.getBytes(StandardCharsets.UTF_8)))throw denied();}
   r.setAttribute(OWNER,access.resolve(session));r.setAttribute(SESSION,session);
   if(write){byte[] bytes=r.getInputStream().readNBytes(4097);if(bytes.length>4096)throw new BusinessException("PAYLOAD_TOO_LARGE","请求内容过长",HttpStatus.PAYLOAD_TOO_LARGE);chain.doFilter(new HttpServletRequestWrapper(r){@Override public ServletInputStream getInputStream(){var stream=new ByteArrayInputStream(bytes);return new ServletInputStream(){public int read(){return stream.read();}public boolean isFinished(){return stream.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){}};}@Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}},s);}
   else chain.doFilter(r,s);
  }catch(BusinessException e){s.setStatus(e.status().value());s.setContentType("application/json");s.setCharacterEncoding("UTF-8");mapper.writeValue(s.getOutputStream(),ApiResponse.failure(e.code(),e.getMessage(),TraceIds.normalize(r.getHeader("X-Trace-Id"))));}
 }
 private static boolean allowed(String method,String path){String uuid="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
  return switch(method){case "GET"->Set.of("/api/v1/alerts","/api/v1/notification-preferences","/api/v1/messages","/api/v1/messages/unread-count").contains(path)||path.matches("/api/v1/alerts/"+uuid+"/triggers")||path.matches("/api/v1/messages/"+uuid+"/email-delivery");case "POST"->Set.of("/api/v1/alerts","/api/v1/messages/read").contains(path);case "PATCH"->path.equals("/api/v1/notification-preferences")||path.matches("/api/v1/alerts/"+uuid)||path.matches("/api/v1/messages/"+uuid+"/read");case "OPTIONS"->Set.of("GET","POST","PATCH").stream().anyMatch(m->allowed(m,path));default->false;};
 }
 private static String cookie(HttpServletRequest r,String name){String value=null;int count=0;if(r.getCookies()!=null)for(var c:r.getCookies())if(name.equals(c.getName())){value=c.getValue();count++;}return count==1?value:null;}
 private static BusinessException denied(){return new BusinessException("ACCESS_DENIED","请求来源或验证信息无效，请刷新后重试",HttpStatus.FORBIDDEN);}
}
