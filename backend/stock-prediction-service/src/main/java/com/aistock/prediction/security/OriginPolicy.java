package com.aistock.prediction.security;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

public final class OriginPolicy extends OncePerRequestFilter {
    private final Set<String> origins;
    private final SecurityErrorWriter errors;
    public OriginPolicy(Set<String> origins,SecurityErrorWriter errors){this.origins=origins;this.errors=errors;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        boolean write=!Set.of("GET","HEAD","OPTIONS","TRACE").contains(request.getMethod());
        String origin=request.getHeader("Origin");
        if(write&&new org.springframework.web.util.UrlPathHelper().getPathWithinApplication(request).startsWith("/api/")&&(origin==null||!origins.contains(origin))){
            errors.write(request,response,403,"ACCESS_DENIED","请求来源不允许");return;
        }
        chain.doFilter(request,response);
    }
}
