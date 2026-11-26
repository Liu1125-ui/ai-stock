package com.aistock.market.alert;
import com.aistock.common.*;import org.springframework.web.bind.annotation.*;
@RestController public class AlertBoundaryController {
 private final AlertBoundaryService service;public AlertBoundaryController(AlertBoundaryService service){this.service=service;}
 @GetMapping("/internal/v1/market/alert-boundary/{instrumentId}") public ApiResponse<AlertBoundaryService.Boundary> get(@PathVariable String instrumentId,@RequestHeader(value="X-Trace-Id",required=false)String trace){return ApiResponse.success(service.get(instrumentId),TraceIds.normalize(trace));}
}
