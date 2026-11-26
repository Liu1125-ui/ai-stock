package com.aistock.market.comparison;
import com.aistock.common.*;
import com.fasterxml.jackson.databind.*;import com.fasterxml.jackson.core.JsonParser;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
public class ComparisonRefreshController {
 private final ComparisonRefreshService service;private final ComparisonRefreshRepository records;
 private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
  .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
 public ComparisonRefreshController(ComparisonRefreshService service,ComparisonRefreshRepository records){this.service=service;this.records=records;}
 @PostMapping("/internal/v1/market/comparison-refresh")
 public ApiResponse<ComparisonUpdateRecords.Result> refresh(@RequestBody byte[] body,jakarta.servlet.http.HttpServletRequest req){
  if(!req.getParameterMap().isEmpty())throw invalid();
  ComparisonUpdateRecords.Request r;
  try{
   var root=mapper.readTree(body);
   for(var field:List.of("operationId","rawVersionId"))if(!root.path(field).isTextual()||!root.path(field).textValue().matches("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}"))throw invalid();
   for(var field:List.of("start","end"))if(!root.path(field).isTextual()||!root.path(field).textValue().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw invalid();
   r=mapper.treeToValue(root,ComparisonUpdateRecords.Request.class);
  }catch(Exception e){throw invalid();}
  return ApiResponse.success(service.refresh(r),TraceIds.normalize(req.getHeader("X-Trace-Id")));
 }
 @GetMapping("/internal/v1/market/comparison-refresh/{operationId}")
 public ApiResponse<ComparisonUpdateRecords.Result> receipt(@PathVariable String operationId,jakarta.servlet.http.HttpServletRequest req){
  if(!req.getParameterMap().isEmpty()||!operationId.matches("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}"))throw invalid();
  return ApiResponse.success(records.lookup(UUID.fromString(operationId)),TraceIds.normalize(req.getHeader("X-Trace-Id")));
 }
 private static BusinessException invalid(){return ComparisonEvidenceValidator.invalid("COMPARISON_REQUEST_INVALID");}
}
