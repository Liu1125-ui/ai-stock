package com.aistock.prediction.security;
import com.aistock.common.BusinessException;
import com.aistock.prediction.modelcatalog.ModelRecords.Actor;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
public final class PredictionSessionFilter extends OncePerRequestFilter {
    private final SessionIdentityClient client;private final String serviceToken;private final SecurityErrorWriter errors;
    public PredictionSessionFilter(SessionIdentityClient client,String serviceToken,SecurityErrorWriter errors){this.client=client;this.serviceToken=serviceToken;this.errors=errors;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException{
        boolean statistics=request.getRequestURI().equals("/api/v1/admin/statistics")||request.getRequestURI().startsWith("/api/v1/admin/statistics/");
        String path=new org.springframework.web.util.UrlPathHelper().getPathWithinApplication(request);
        boolean training=path.equals("/api/v1/training-submissions")||path.startsWith("/api/v1/training-submissions/");
        if(statistics){response.setHeader("Cache-Control","no-store");if(!"GET".equals(request.getMethod())||!request.getRequestURI().equals("/api/v1/admin/statistics/predictions")){errors.write(request,response,404,"RESOURCE_NOT_FOUND","接口不存在");return;}
        }
        if(statistics||training){response.setHeader("Cache-Control","no-store");
            int count=0;if(request.getCookies()!=null)for(var c:request.getCookies())if("AISTOCK_SESSION".equals(c.getName()))count++;
            if(count!=1||Collections.list(request.getHeaders("Cookie")).size()>1){errors.write(request,response,401,"AUTH_REQUIRED","请先登录");return;}}
        String cookie=null;if(request.getCookies()!=null)for(var c:request.getCookies())if("AISTOCK_SESSION".equals(c.getName()))cookie=c.getValue();
        if(cookie!=null&&!cookie.isBlank()){
            if(serviceToken.isBlank()){unavailable(request,response);return;}
            try{
                var reply=client.resolve(new SessionIdentityClient.ResolveInput(cookie),serviceToken);
                if(reply==null||!"OK".equals(reply.code())||reply.data()==null||reply.data().userId()==null||!Set.of("USER","ADMIN").contains(reply.data().role())){unavailable(request,response);return;}
                if(statistics&&reply.data().credentialVersion()<0){unavailable(request,response);return;}
                var identity=reply.data();
                var authorities=new ArrayList<SimpleGrantedAuthority>();
                authorities.add(new SimpleGrantedAuthority("ROLE_"+identity.role()));
                if("ADMIN".equals(identity.role())||identity.experimentAllowed())
                    authorities.add(new SimpleGrantedAuthority(com.aistock.prediction.training.TrainingAdmissionController.EXPERIMENT_AUTHORITY));
                var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(new Actor(identity.userId(),identity.role()),null,authorities));SecurityContextHolder.setContext(context);
            }catch(feign.FeignException e){if(e.status()!=401){unavailable(request,response);return;}}
            catch(BusinessException e){if(e.status().value()!=401){unavailable(request,response);return;}}
            catch(RuntimeException e){unavailable(request,response);return;}
        }
        response.setHeader("Cache-Control","no-store");chain.doFilter(request,response);
    }
    private void unavailable(HttpServletRequest r,HttpServletResponse s)throws IOException{errors.write(r,s,503,"USER_SERVICE_UNAVAILABLE","账号服务暂不可用");}
}
