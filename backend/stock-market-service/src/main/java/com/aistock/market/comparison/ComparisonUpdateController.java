package com.aistock.market.comparison;
import com.aistock.common.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
public class ComparisonUpdateController {
 private final ComparisonUpdateQuery query;
 public ComparisonUpdateController(ComparisonUpdateQuery query){this.query=query;}
 @GetMapping("/internal/v1/market/comparison-update-options")
 public ApiResponse<ComparisonUpdateRecords.Options> options(@RequestParam String ids,jakarta.servlet.http.HttpServletRequest request,
   @RequestHeader(value="X-Trace-Id",required=false)String trace){
  if(!request.getParameterMap().keySet().equals(Set.of("ids"))||request.getParameterMap().values().stream().anyMatch(v->v.length!=1)||ids.length()>449)
   throw ComparisonEvidenceValidator.invalid("COMPARISON_REQUEST_INVALID");
  var instruments=List.of(ids.split(",",-1));ComparisonUpdateQuery.validateIds(instruments);
  return ApiResponse.success(query.options(instruments),TraceIds.normalize(trace));
 }
}
