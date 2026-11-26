package com.aistock.notification.alert;
import com.aistock.common.*;import com.fasterxml.jackson.databind.ObjectMapper;import jakarta.servlet.*;import jakarta.servlet.http.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.util.*;import org.springframework.beans.factory.annotation.Value;import org.springframework.core.annotation.Order;import org.springframework.stereotype.Component;import org.springframework.web.filter.OncePerRequestFilter;
@Component @Order(-110) public class AlertEvaluationTokenFilter extends OncePerRequestFilter {
 private final String token;private final ObjectMapper mapper;
 public AlertEvaluationTokenFilter(@Value("${aistock.internal.token:}")String token,ObjectMapper mapper){this.token=token;this.mapper=mapper;}
 @Override protected boolean shouldNotFilter(HttpServletRequest request){String path=new org.springframework.web.util.UrlPathHelper().getPathWithinApplication(request);return !(path.startsWith("/internal/v1/alerts")||path.startsWith("/internal/v1/alert-evaluations"));}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  response.setHeader("Cache-Control","no-store");var values=Collections.list(request.getHeaders("X-Service-Token"));String supplied=values.size()==1?values.get(0):null;
  if(token==null||token.isBlank()||supplied==null||supplied.length()>512||!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))){failure(request,response,401,"INTERNAL_AUTH_REQUIRED");return;}
  if(Set.of("GET","HEAD","OPTIONS").contains(request.getMethod())){chain.doFilter(request,response);return;}byte[] bytes=request.getInputStream().readNBytes(4097);if(bytes.length>4096){failure(request,response,413,"PAYLOAD_TOO_LARGE");return;}
  chain.doFilter(new HttpServletRequestWrapper(request){@Override public ServletInputStream getInputStream(){var stream=new ByteArrayInputStream(bytes);return new ServletInputStream(){public int read(){return stream.read();}public boolean isFinished(){return stream.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){}};}@Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}},response);
 }
 private void failure(HttpServletRequest request,HttpServletResponse response,int status,String code)throws IOException{response.setStatus(status);response.setContentType("application/json");response.setCharacterEncoding("UTF-8");mapper.writeValue(response.getWriter(),ApiResponse.failure(code,"内部请求未通过核验",TraceIds.normalize(request.getHeader("X-Trace-Id"))));}
}
