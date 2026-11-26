package com.aistock.notification.alert;
import com.aistock.common.*;import org.springframework.web.bind.annotation.*;
@RestController public class AlertRuleScanController {
 private final AlertEvaluationService service;public AlertRuleScanController(AlertEvaluationService service){this.service=service;}
 @GetMapping("/internal/v1/alerts/due") public ApiResponse<AlertEvaluationRecords.RulePage> due(@RequestParam(required=false)String after,@RequestParam(defaultValue="50")int limit,@RequestParam(required=false)String instrumentId,@RequestHeader(value="X-Trace-Id",required=false)String trace){return ApiResponse.success(service.due(after,limit,instrumentId),TraceIds.normalize(trace));}
}
