package com.aistock.user.alert;

import com.aistock.common.ApiResponse;
import com.aistock.common.TraceIds;
import com.aistock.user.account.EmailAddress;
import jakarta.servlet.http.HttpServletRequest;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

/** Internal-only recipient context. Never added to the public gateway. */
@RestController
public class AlertMailContextController {
 private final JdbcTemplate jdbc; private final Clock clock;
 public AlertMailContextController(JdbcTemplate jdbc,Clock authClock){this.jdbc=jdbc;this.clock=authClock;}
 public record AccountContext(UUID userId,String status,long revision,boolean emailVerified,Instant checkedAt){}
 public record MailTarget(UUID userId,String status,long revision,boolean emailVerified,String email,Instant checkedAt){
  @Override public String toString(){return "MailTarget[userId="+userId+", revision="+revision+", recipient=redacted]";}
 }
 private record Current(UUID id,String status,long revision,Instant verifiedAt,String email){}
 @GetMapping("/internal/v1/users/{id}/alert-context")
 public ResponseEntity<?> context(@PathVariable String id,HttpServletRequest request){return read(id,request,false);}
 @GetMapping("/internal/v1/users/{id}/alert-mail-target")
 public ResponseEntity<?> target(@PathVariable String id,HttpServletRequest request){return read(id,request,true);}
 private ResponseEntity<?> read(String id,HttpServletRequest request,boolean target){
  String trace=TraceIds.normalize(request.getHeader("X-Trace-Id"));
  if(id==null||!id.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))return failure(400,"VALIDATION_FAILED",trace);
  long expected=0;
  if(target){
   var params=request.getParameterMap();var revision=params.get("revision");
   if(params.size()!=1||revision==null||revision.length!=1||!revision[0].matches("0|[1-9][0-9]{0,18}"))return failure(400,"VALIDATION_FAILED",trace);
   try{expected=Long.parseLong(revision[0]);}catch(NumberFormatException invalid){return failure(400,"VALIDATION_FAILED",trace);}
  }else if(!request.getParameterMap().isEmpty())return failure(400,"VALIDATION_FAILED",trace);
  try{
   // Context deliberately does not select email or credential columns.
   var rows=jdbc.query("select user_id,status,revision,email_verified_at"+(target?",email_normalized":"")+" from user_account where user_id=?",
    (rs,n)->new Current(UUID.fromString(rs.getString("user_id")),rs.getString("status"),rs.getLong("revision"),rs.getTimestamp("email_verified_at")==null?null:rs.getTimestamp("email_verified_at").toInstant(),target?rs.getString("email_normalized"):null),id.toLowerCase(Locale.ROOT));
   if(rows.isEmpty())return failure(404,"RESOURCE_NOT_FOUND",trace);
   var row=rows.get(0);var now=clock.instant();
   if(!Set.of("ENABLED","DISABLED").contains(row.status())||row.revision()<0)return failure(503,"ALERT_ACCOUNT_UNAVAILABLE",trace);
   if(target&&row.revision()!=expected)return failure(409,"ALERT_RECIPIENT_CHANGED",trace);
   boolean verified=row.verifiedAt()!=null&&!row.verifiedAt().isAfter(now);
   Object data;
   if(target){String email=null;if(verified){try{email=EmailAddress.normalize(row.email());}catch(com.aistock.common.BusinessException invalid){return failure(503,"ALERT_ACCOUNT_UNAVAILABLE",trace);}}data=new MailTarget(row.id(),row.status(),row.revision(),verified,email,now);}
   else data=new AccountContext(row.id(),row.status(),row.revision(),verified,now);
   return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data,trace));
  }catch(DataAccessException unavailable){return failure(503,"ALERT_ACCOUNT_UNAVAILABLE",trace);}
 }
 private static ResponseEntity<?> failure(int status,String code,String trace){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(ApiResponse.failure(code,"账号核验暂不可用或请求无效",trace));}
}
