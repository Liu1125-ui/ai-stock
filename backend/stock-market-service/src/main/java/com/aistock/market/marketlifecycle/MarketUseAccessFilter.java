package com.aistock.market.marketlifecycle;

import com.aistock.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE+9)
public final class MarketUseAccessFilter extends OncePerRequestFilter {
    static final String BASE="/internal/v1/market-version-uses";
    private final String token;private final ObjectMapper json;
    public MarketUseAccessFilter(@Value("${aistock.internal.token:}")String token,ObjectMapper json){this.token=token;this.json=json;}
    @Override protected boolean shouldNotFilter(HttpServletRequest r){var path=new UrlPathHelper().getPathWithinApplication(r);return !path.equals(BASE)&&!path.startsWith(BASE+"/");}
    @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain)throws IOException,ServletException {
        s.setHeader("Cache-Control","no-store");var headers=Collections.list(r.getHeaders("X-Service-Token"));
        if(token==null||token.isBlank()){deny(r,s,503,"INTERNAL_AUTH_NOT_CONFIGURED");return;}
        if(headers.size()!=1||!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),headers.get(0).getBytes(StandardCharsets.UTF_8))){deny(r,s,401,"INTERNAL_AUTH_REQUIRED");return;}
        if(!Set.of("GET","POST").contains(r.getMethod())){deny(r,s,404,"RESOURCE_NOT_FOUND");return;}
        if("POST".equals(r.getMethod())) {
            var types=Collections.list(r.getHeaders("Content-Type"));
            try {if(types.size()!=1)throw new IllegalArgumentException();var type=MediaType.parseMediaType(types.get(0));if(!"application".equals(type.getType())||!"json".equals(type.getSubtype())||type.getCharset()!=null&&!StandardCharsets.UTF_8.equals(type.getCharset()))throw new IllegalArgumentException();}
            catch(IllegalArgumentException e){deny(r,s,400,"MARKET_VERSION_REQUEST_INVALID");return;}
        }
        if(r.getContentLengthLong()>65536){deny(r,s,413,"MARKET_PAYLOAD_TOO_LARGE");return;}
        var body=r.getInputStream().readNBytes(65537);if(body.length>65536){deny(r,s,413,"MARKET_PAYLOAD_TOO_LARGE");return;}
        if("GET".equals(r.getMethod())&&body.length!=0){deny(r,s,400,"MARKET_VERSION_REQUEST_INVALID");return;}
        chain.doFilter(new HttpServletRequestWrapper(r){
            @Override public ServletInputStream getInputStream(){var input=new ByteArrayInputStream(body);return new ServletInputStream(){
                public int read(){return input.read();}public int read(byte[] b,int o,int n){return input.read(b,o,n);}public boolean isReady(){return true;}public boolean isFinished(){return input.available()==0;}public void setReadListener(ReadListener listener){throw new UnsupportedOperationException("Synchronous internal protocol");}
            };}
            @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
        },s);
    }
    private void deny(HttpServletRequest r,HttpServletResponse s,int status,String code)throws IOException {s.setStatus(status);s.setContentType("application/json;charset=UTF-8");json.writeValue(s.getOutputStream(),ApiResponse.failure(code,"内部版本操作请求不可用，已有状态保留",TraceIds.normalize(r.getHeader("X-Trace-Id"))));}
}
