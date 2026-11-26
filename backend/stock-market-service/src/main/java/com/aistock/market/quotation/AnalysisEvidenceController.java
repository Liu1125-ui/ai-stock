package com.aistock.market.quotation;
import com.aistock.common.*;import org.springframework.web.bind.annotation.*;
@RestController public class AnalysisEvidenceController {
 private final MarketQueryService service;
 public AnalysisEvidenceController(MarketQueryService service){this.service=service;}
 @GetMapping("/internal/v1/market/analysis-evidence/{instrumentId}")
 public ApiResponse<?> evidence(@PathVariable String instrumentId,@RequestParam String from,@RequestParam String to,@RequestParam String versionId,@RequestHeader(value="X-Trace-Id",required=false)String trace){return ApiResponse.success(service.daily(instrumentId,from,to,"RAW",versionId),TraceIds.normalize(trace));}
}