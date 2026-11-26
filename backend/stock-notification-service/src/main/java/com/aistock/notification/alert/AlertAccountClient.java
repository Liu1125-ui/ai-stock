package com.aistock.notification.alert;
import java.time.*;import java.util.*;
public final class AlertAccountClient {
 private final AlertRemoteHttp http;private final Clock clock;
 public AlertAccountClient(AlertRemoteHttp http,Clock clock){this.http=http;this.clock=clock;}
 public record AccountStatus(UUID userId,String status,Instant checkedAt){}
 public AccountStatus status(UUID id){if(id==null)throw AlertRemoteHttp.invalid();var n=http.get("stock-user-service","/internal/v1/users/"+id+"/alert-status");try{
  if(n.size()!=3||!n.path("userId").isTextual()||!AlertRemoteHttp.uuid(n.path("userId").textValue())||!n.path("status").isTextual()||!n.path("checkedAt").isTextual())throw AlertRemoteHttp.invalid();
  var result=http.mapper.treeToValue(n,AccountStatus.class);if(!id.equals(result.userId())||!Set.of("ENABLED","DISABLED").contains(result.status())||result.checkedAt()==null||result.checkedAt().isBefore(clock.instant().minusSeconds(5))||result.checkedAt().isAfter(clock.instant().plusSeconds(5)))throw AlertRemoteHttp.invalid();return result;
 }catch(com.aistock.common.BusinessException error){throw error;}catch(Exception error){throw AlertRemoteHttp.invalid();}}
}
