package com.aistock.user.security;
import com.aistock.common.BusinessException;
import com.aistock.user.session.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class SessionAuthenticationFilter extends OncePerRequestFilter {
    private final SessionService sessions;private final SecurityErrorWriter errors;
    public SessionAuthenticationFilter(SessionService sessions,SecurityErrorWriter errors){this.sessions=sessions;this.errors=errors;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        if(request.getRequestURI().equals("/api/v1/admin/statistics")||request.getRequestURI().startsWith("/api/v1/admin/statistics/")){
            response.setHeader("Cache-Control","no-store");
            if(!"GET".equals(request.getMethod())||!"/api/v1/admin/statistics/users".equals(request.getRequestURI())){errors.write(request,response,404,"RESOURCE_NOT_FOUND","接口不存在");return;}
            int count=0;if(request.getCookies()!=null)for(var c:request.getCookies())if(CookieWriter.SESSION.equals(c.getName()))count++;
            if(count!=1||java.util.Collections.list(request.getHeaders("Cookie")).size()>1){errors.write(request,response,401,"AUTH_REQUIRED","请先登录");return;}
        }
        String token=CookieWriter.read(request);
        if(token!=null){
            try{
                var identity=sessions.resolve(token);
                var context=SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(identity,null,List.of(new SimpleGrantedAuthority("ROLE_"+identity.role()))));
                SecurityContextHolder.setContext(context);
            }catch(BusinessException unauthenticated){
                if(!"AUTH_REQUIRED".equals(unauthenticated.code())){errors.write(request,response,503,"USER_SERVICE_UNAVAILABLE","账号服务暂不可用");return;}
            }catch(RuntimeException unavailable){errors.write(request,response,503,"USER_SERVICE_UNAVAILABLE","账号服务暂不可用");return;}
        }
        chain.doFilter(request,response);
    }
}
