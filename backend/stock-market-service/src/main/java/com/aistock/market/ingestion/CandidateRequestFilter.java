package com.aistock.market.ingestion;

import com.aistock.common.ApiResponse;
import com.aistock.common.TraceIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/** Authenticate before JSON parsing; buffer only the bounded candidate body. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE+10)
public class CandidateRequestFilter extends OncePerRequestFilter {
    private static final UrlPathHelper INTERNAL_PATH = new UrlPathHelper();
    private final String token;private final int limit;private final ObjectMapper mapper=new ObjectMapper();
    public CandidateRequestFilter(@Value("${aistock.internal.token:}")String token,
            @Value("${aistock.market.candidate-max-bytes:2097152}")int limit) {
        if(limit<1 || limit>16777216)throw new IllegalArgumentException("Invalid candidate body limit");
        this.token=token;this.limit=limit;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !INTERNAL_PATH.getPathWithinApplication(request).startsWith("/internal/v1/market/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        var path=INTERNAL_PATH.getPathWithinApplication(request);
        // Establish cache policy before authentication, dispatch and exception handling.
        if(path.equals("/internal/v1/market/admin-refresh-context")
                || path.startsWith("/internal/v1/market/admin-refresh-context/")) {
            response.setHeader("Cache-Control","no-store");
        }
        var supplied=request.getHeader("X-Service-Token");
        if(token.isBlank()){deny(request,response,503,"INTERNAL_AUTH_NOT_CONFIGURED");return;}
        if(supplied==null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))) {
            deny(request,response,401,"INTERNAL_AUTH_REQUIRED");return;
        }
        int requestLimit=path.startsWith("/internal/v1/market/calendar")||path.startsWith("/internal/v1/market/index-snapshot")||path.startsWith("/internal/v1/market/alert-evidence")||path.equals("/internal/v1/market/comparison-refresh")||path.equals("/internal/v1/market/valuation-quotes")?Math.min(limit,4096):limit;
        if(request.getContentLengthLong()>requestLimit){deny(request,response,413,"MARKET_PAYLOAD_TOO_LARGE");return;}
        var body=request.getInputStream().readNBytes(requestLimit+1);
        if(body.length>requestLimit){deny(request,response,413,"MARKET_PAYLOAD_TOO_LARGE");return;}
        var wrapped=new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                var input=new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    @Override public int read(){return input.read();}
                    @Override public int read(byte[] bytes,int offset,int length){return input.read(bytes,offset,length);}
                    @Override public boolean isFinished(){return input.available()==0;}
                    @Override public boolean isReady(){return true;}
                    @Override public void setReadListener(ReadListener listener){throw new UnsupportedOperationException("Synchronous candidate input");}
                };
            }
            @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
        };
        chain.doFilter(wrapped,response);
    }
    private void deny(HttpServletRequest request,HttpServletResponse response,int status,String code)throws IOException {
        response.setStatus(status);response.setContentType("application/json");response.setCharacterEncoding("UTF-8");response.setHeader("Cache-Control","no-store");
        mapper.writeValue(response.getWriter(),ApiResponse.failure(code,status==413?"行情候选超过当前处理上限":"缺少有效服务身份",TraceIds.normalize(request.getHeader("X-Trace-Id"))));
    }
}
