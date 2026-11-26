package com.aistock.gateway;
import java.util.Map;import org.springframework.cloud.openfeign.FeignClient;import org.springframework.http.ResponseEntity;import org.springframework.util.MultiValueMap;import org.springframework.web.bind.annotation.*;
@FeignClient(name="stock-scheduler-service",url="${aistock.scheduler.url:}",configuration=SchedulerPublicFeignConfiguration.class)
public interface SchedulerPublicClient {
 @GetMapping("/api/v1/{path}")ResponseEntity<byte[]> get(@PathVariable("path")String path,@RequestParam MultiValueMap<String,String> query,@RequestHeader Map<String,String> headers);
 @PostMapping("/api/v1/{path}")ResponseEntity<byte[]> post(@PathVariable("path")String path,@RequestBody byte[] body,@RequestHeader Map<String,String> headers);
 @RequestMapping(value="/api/v1/{path}",method=RequestMethod.OPTIONS)ResponseEntity<byte[]> options(@PathVariable("path")String path,@RequestHeader Map<String,String> headers);
}
