package com.aistock.market.alert;
import com.aistock.common.*;import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;import java.util.*;
import org.springframework.web.bind.annotation.*;import org.springframework.http.HttpStatus;
@RestController @RequestMapping("/internal/v1/market/alert-evidence") public class AlertEvidenceController {
 private final AlertEvidenceService service;public AlertEvidenceController(AlertEvidenceService service){this.service=service;}
 @PostMapping("/prepare") public ApiResponse<AlertEvidenceRecords.Receipt> prepare(@RequestBody JsonNode body,@RequestHeader(value="X-Trace-Id",required=false)String trace){
  var fields=new HashSet<String>();if(body==null||!body.isObject())throw invalid();body.fieldNames().forEachRemaining(fields::add);
  if(!fields.equals(Set.of("operationId","instrumentId","rawVersionId","tradeDate","leaseUntil")))throw invalid();for(var name:fields)if(!body.get(name).isTextual())throw invalid();
  AlertEvidenceRecords.Request request;try{var date=body.get("tradeDate").textValue();if(!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw invalid();request=new AlertEvidenceRecords.Request(uuid(body.get("operationId").textValue()),body.get("instrumentId").textValue(),uuid(body.get("rawVersionId").textValue()),LocalDate.parse(date),Instant.parse(body.get("leaseUntil").textValue()));}catch(java.time.DateTimeException error){throw invalid();}
  return ApiResponse.success(service.prepare(request),TraceIds.normalize(trace));
 }
 @GetMapping("/operations/{id}") public ApiResponse<AlertEvidenceRecords.Receipt> operation(@PathVariable String id,@RequestHeader(value="X-Trace-Id",required=false)String trace){return ApiResponse.success(service.operation(uuid(id)),TraceIds.normalize(trace));}
 @GetMapping("/versions/{id}") public ApiResponse<AlertEvidenceRecords.Evidence> version(@PathVariable String id,@RequestHeader(value="X-Trace-Id",required=false)String trace){return ApiResponse.success(service.version(uuid(id)),TraceIds.normalize(trace));}
 private static UUID uuid(String value){if(value==null||!value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))throw invalid();return UUID.fromString(value);}
 private static BusinessException invalid(){return new BusinessException("VALIDATION_FAILED","预警证据请求格式不正确",HttpStatus.BAD_REQUEST);}
}
