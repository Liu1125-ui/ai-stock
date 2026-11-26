package com.aistock.prediction.security;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
public final class ModelRequestFilter extends OncePerRequestFilter {
    private final String token;private final SecurityErrorWriter errors;
    public ModelRequestFilter(String token,SecurityErrorWriter errors){this.token=token;this.errors=errors;}
    @Override protected boolean shouldNotFilter(HttpServletRequest request){return !new UrlPathHelper().getPathWithinApplication(request).startsWith("/internal/");}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException{
        var supplied=request.getHeader("X-Service-Token");
        if(token.isBlank()){errors.write(request,response,503,"INTERNAL_AUTH_NOT_CONFIGURED","内部服务认证未配置");return;}
        if(supplied==null||!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))){errors.write(request,response,401,"INTERNAL_AUTH_REQUIRED","缺少有效服务身份");return;}
        chain.doFilter(request,response);
    }
}
