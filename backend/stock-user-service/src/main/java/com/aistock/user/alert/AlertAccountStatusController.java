package com.aistock.user.alert;
import com.aistock.common.*;import com.aistock.user.account.UserRepository;import java.time.*;import java.util.*;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.http.*;import org.springframework.web.bind.annotation.*;
@RestController public class AlertAccountStatusController {
 private final UserRepository users;private final Clock clock;
 public AlertAccountStatusController(JdbcTemplate jdbc,Clock authClock){users=new UserRepository(jdbc);clock=authClock;}
 public record AccountStatus(UUID userId,String status,Instant checkedAt){}
 @GetMapping("/internal/v1/users/{id}/alert-status") public ResponseEntity<?> status(@PathVariable String id,@RequestHeader(value="X-Trace-Id",required=false)String trace){
  String normalized=TraceIds.normalize(trace);
  if(id==null||!id.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))return failure(400,"VALIDATION_FAILED",normalized);
  try{var user=users.findById(UUID.fromString(id));if(user.isEmpty())return failure(404,"RESOURCE_NOT_FOUND",normalized);var account=user.get();if(!Set.of("ENABLED","DISABLED").contains(account.status()))return failure(503,"ALERT_ACCOUNT_UNAVAILABLE",normalized);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(new AccountStatus(account.userId(),account.status(),clock.instant()),normalized));}
  catch(org.springframework.dao.DataAccessException unavailable){return failure(503,"ALERT_ACCOUNT_UNAVAILABLE",normalized);}
 }
 private static ResponseEntity<?> failure(int code,String reason,String trace){return ResponseEntity.status(code).cacheControl(CacheControl.noStore()).body(ApiResponse.failure(reason,"账号状态暂不可用或请求无效",trace));}
}

