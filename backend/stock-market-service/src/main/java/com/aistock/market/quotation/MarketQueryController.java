package com.aistock.market.quotation;
import com.aistock.common.*;import java.util.List;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/instruments") public class MarketQueryController {
 private final MarketQueryService service;public MarketQueryController(MarketQueryService service){this.service=service;}
 @GetMapping public ApiResponse<?> search(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,@RequestHeader(value="X-Trace-Id",required=false)String trace){return ok(service.search(q,page,size),trace);}
 @GetMapping("/summaries")public ApiResponse<?> summaries(@RequestParam List<String> ids,@RequestHeader(value="X-Trace-Id",required=false)String trace){return ok(service.summaries(ids),trace);}
 @GetMapping("/{id}")public ApiResponse<?> instrument(@PathVariable String id,@RequestHeader(value="X-Trace-Id",required=false)String trace){return ok(service.instrument(id),trace);}
 @GetMapping("/{id}/daily")public ApiResponse<?> daily(@PathVariable String id,@RequestParam(required=false)String from,@RequestParam(required=false)String to,@RequestParam(defaultValue="RAW")String basis,@RequestParam(required=false)String versionId,@RequestHeader(value="X-Trace-Id",required=false)String trace){return ok(service.daily(id,from,to,basis,versionId),trace);}
 private ApiResponse<?> ok(Object data,String trace){return ApiResponse.success(data,TraceIds.normalize(trace));}
}
