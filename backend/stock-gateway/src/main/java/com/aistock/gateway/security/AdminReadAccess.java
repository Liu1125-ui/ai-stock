package com.aistock.gateway.security;
import com.aistock.gateway.statistics.StatisticsIdentityClient;
import com.aistock.common.*;import jakarta.servlet.http.*;import java.util.*;import org.springframework.http.HttpStatus;
public final class AdminReadAccess {
 private final StatisticsIdentityClient client;private final String token;
 public AdminReadAccess(StatisticsIdentityClient client,String token){this.client=client;this.token=token;}
 public void requireAdmin(HttpServletRequest r){String session=null;int count=0;if(r.getCookies()!=null)for(var c:r.getCookies())if("AISTOCK_SESSION".equals(c.getName())){count++;session=c.getValue();}
  if(count!=1||session==null||session.isBlank()||session.length()>4096||Collections.list(r.getHeaders("Cookie")).size()>1)throw auth();
  if(token==null||token.isBlank())throw unavailable();StatisticsIdentityClient.Identity id;
  try{var reply=client.resolve(new StatisticsIdentityClient.ResolveInput(session),token);if(reply!=null&&"AUTH_REQUIRED".equals(reply.code()))throw auth();if(reply==null||!"OK".equals(reply.code())||reply.data()==null)throw unavailable();id=reply.data();}
  catch(feign.FeignException e){if(e.status()==401)throw auth();throw unavailable();}catch(BusinessException e){if("AUTH_REQUIRED".equals(e.code()))throw auth();throw unavailable();}catch(RuntimeException e){throw unavailable();}
  if(id.userId()==null||id.credentialVersion()<0||id.role()==null||!Set.of("ADMIN","USER").contains(id.role()))throw unavailable();if(!"ADMIN".equals(id.role()))throw new BusinessException("ACCESS_DENIED","仅管理员可以访问此功能",HttpStatus.FORBIDDEN);
 }
 private static BusinessException auth(){return new BusinessException("AUTH_REQUIRED","请先登录",HttpStatus.UNAUTHORIZED);}
 private static BusinessException unavailable(){return new BusinessException("USER_SERVICE_UNAVAILABLE","账号服务暂不可用",HttpStatus.SERVICE_UNAVAILABLE);}
}
