package com.aistock.scheduler.admin;
import com.aistock.common.*;
import com.aistock.scheduler.security.AdminTaskPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;import java.time.Clock;import java.util.*;
import org.springframework.http.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.aistock.scheduler.admin.AdminTaskActionRecords.*;

@RestController
@RequestMapping("/api/v1/admin/tasks")
public class AdminTaskActionController {
 private final AdminTaskActionService service;private final Clock clock;
 public AdminTaskActionController(AdminTaskActionService service,Clock clock){this.service=service;this.clock=clock;}
 @GetMapping("/{id}/action-options")
 public ApiResponse<Options> options(@PathVariable String id,@RequestParam MultiValueMap<String,String> query,HttpServletRequest r) {
  principal(r);none(query);return ok(service.options(AdminTaskActionInput.validateId(id),clock.instant()),r);
 }
 @PostMapping("/{id}/retry")
 public ResponseEntity<ApiResponse<Receipt>> retry(@PathVariable String id,@RequestParam MultiValueMap<String,String> query,HttpServletRequest r)throws IOException{return execute(id,Action.RETRY,query,r);}
 @PostMapping("/{id}/cancel")
 public ResponseEntity<ApiResponse<Receipt>> cancel(@PathVariable String id,@RequestParam MultiValueMap<String,String> query,HttpServletRequest r)throws IOException{return execute(id,Action.CANCEL,query,r);}
 private ResponseEntity<ApiResponse<Receipt>> execute(String id,Action action,MultiValueMap<String,String> query,HttpServletRequest r)throws IOException {
  var actor=principal(r);none(query);var task=AdminTaskActionInput.validateId(id);
  var input=AdminTaskActionInput.parse(r.getInputStream().readNBytes(4097),action);
  var result=service.execute(actor.userId(),task,action,input,clock.instant());
  var envelope=new ApiResponse<Receipt>(result.code(),result.receipt().message(),result.receipt(),trace(r));
  return ResponseEntity.status(result.status()).cacheControl(CacheControl.noStore()).body(envelope);
 }
 @GetMapping("/{id}/operations/{operation}")
 public ApiResponse<Receipt> receipt(@PathVariable String id,@PathVariable String operation,@RequestParam MultiValueMap<String,String> query,HttpServletRequest r) {
  var actor=principal(r);none(query);return ok(service.receipt(actor.userId(),AdminTaskActionInput.validateId(id),AdminTaskActionInput.validateId(operation)),r);
 }
 @GetMapping("/{id}/operations")
 public ApiResponse<HistoryPage> history(@PathVariable String id,@RequestParam MultiValueMap<String,String> query,HttpServletRequest r) {
  principal(r);for(var entry:query.entrySet())if(!Set.of("limit","cursor").contains(entry.getKey())||entry.getValue().size()!=1)throw AdminTaskActionInput.invalid();
  int limit=20;var raw=query.getFirst("limit");
  if(raw!=null){if(!raw.matches("[1-9][0-9]?"))throw AdminTaskActionInput.invalid();limit=Integer.parseInt(raw);if(limit>50)throw AdminTaskActionInput.invalid();}
  return ok(service.history(AdminTaskActionInput.validateId(id),new HistoryQuery(limit),query.getFirst("cursor"),clock.instant()),r);
 }
 private void none(MultiValueMap<String,String> query){if(!query.isEmpty())throw AdminTaskActionInput.invalid();}
 private AdminTaskPrincipal principal(HttpServletRequest r){var value=r.getAttribute(AdminTaskPrincipal.class.getName());if(value instanceof AdminTaskPrincipal p&&p.userId()!=null)return p;throw new BusinessException("AUTH_REQUIRED","请先登录",HttpStatus.UNAUTHORIZED);}
 private String trace(HttpServletRequest r){return TraceIds.normalize(r.getHeader("X-Trace-Id"));}
 private <T>ApiResponse<T> ok(T data,HttpServletRequest r){return ApiResponse.success(data,trace(r));}
}
