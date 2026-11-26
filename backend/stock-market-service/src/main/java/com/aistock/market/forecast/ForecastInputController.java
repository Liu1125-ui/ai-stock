package com.aistock.market.forecast;
import com.aistock.common.*;
import org.springframework.web.bind.annotation.*;
@RestController
public class ForecastInputController {
 private final ForecastInputService service;
 public ForecastInputController(ForecastInputService service){this.service=service;}
 @GetMapping("/internal/v1/market/forecast-raw-window")
 public ApiResponse<ForecastInputService.RawWindow> rawWindow(@RequestParam String instrumentId,@RequestHeader(value="X-Trace-Id",required=false)String trace){return ApiResponse.success(service.rawWindow(instrumentId),TraceIds.normalize(trace));}
 @PostMapping("/internal/v1/market/forecast-inputs")
 public ApiResponse<ForecastInputService.Publication> publish(@RequestBody ForecastCandidate candidate,@RequestHeader(value="X-Trace-Id",required=false)String trace){return ApiResponse.success(service.publish(candidate),TraceIds.normalize(trace));}
 @GetMapping("/internal/v1/market/forecast-window")
 public ApiResponse<ForecastInputService.Window> window(@RequestParam String instrumentId,@RequestParam(required=false)String inputVersionId,@RequestParam(defaultValue="60")int window,@RequestParam(defaultValue="5")int horizon,@RequestHeader(value="X-Trace-Id",required=false)String trace){return ApiResponse.success(service.window(instrumentId,inputVersionId,window,horizon),TraceIds.normalize(trace));}
}
