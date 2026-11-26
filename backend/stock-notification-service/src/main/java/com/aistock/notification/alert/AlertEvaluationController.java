package com.aistock.notification.alert;
import com.aistock.common.*;import com.fasterxml.jackson.databind.JsonNode;import java.util.*;import java.time.Instant;import org.springframework.web.bind.annotation.*;import static com.aistock.notification.alert.AlertEvaluationRecords.*;
@RestController @RequestMapping("/internal/v1/alert-evaluations") public class AlertEvaluationController {
 private final AlertEvaluationService service;public AlertEvaluationController(AlertEvaluationService service){this.service=service;}
 @PostMapping public ApiResponse<EvaluationReceipt> evaluate(@RequestBody JsonNode body,@RequestHeader(value="X-Trace-Id",required=false)String trace){
  if(body==null||!body.isObject()||body.size()!=5)throw AlertRuleService.invalid();var expected=Set.of("operationId","ruleId","revision","evidenceId","leaseUntil");body.fieldNames().forEachRemaining(k->{if(!expected.contains(k))throw AlertRuleService.invalid();});
  for(String key:List.of("operationId","ruleId","evidenceId"))if(!body.path(key).isTextual()||!AlertRemoteHttp.uuid(body.get(key).textValue()))throw AlertRuleService.invalid();
  if(!body.path("revision").isIntegralNumber()||!body.get("revision").canConvertToLong()||body.get("revision").longValue()<0||!body.path("leaseUntil").isTextual())throw AlertRuleService.invalid();Instant until;try{until=Instant.parse(body.get("leaseUntil").textValue());}catch(Exception invalid){throw AlertRuleService.invalid();}
  var request=new EvaluateRequest(UUID.fromString(body.get("operationId").textValue()),UUID.fromString(body.get("ruleId").textValue()),body.get("revision").longValue(),UUID.fromString(body.get("evidenceId").textValue()),until);return ApiResponse.success(service.evaluate(request),TraceIds.normalize(trace));
 }
 @GetMapping("/operations/{id}") public ApiResponse<EvaluationReceipt> receipt(@PathVariable String id,@RequestHeader(value="X-Trace-Id",required=false)String trace){if(!AlertRemoteHttp.uuid(id))throw AlertRuleService.invalid();return ApiResponse.success(service.receipt(UUID.fromString(id)),TraceIds.normalize(trace));}
}
