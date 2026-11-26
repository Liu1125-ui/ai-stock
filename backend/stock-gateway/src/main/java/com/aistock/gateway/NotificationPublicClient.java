package com.aistock.gateway;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@FeignClient(name="stock-notification-service",url="${aistock.notification.url:}")
public interface NotificationPublicClient {
    @GetMapping("/api/v1/{path}") ResponseEntity<byte[]> get(@PathVariable("path")String path,@RequestParam Map<String,String> query,@RequestHeader Map<String,String> headers);
    @PostMapping("/api/v1/{path}") ResponseEntity<byte[]> post(@PathVariable("path")String path,@RequestParam Map<String,String> query,@RequestHeader Map<String,String> headers,@RequestBody byte[] body);
    @PatchMapping("/api/v1/{path}") ResponseEntity<byte[]> patch(@PathVariable("path")String path,@RequestParam Map<String,String> query,@RequestHeader Map<String,String> headers,@RequestBody byte[] body);
    @RequestMapping(value="/api/v1/{path}",method=RequestMethod.OPTIONS) ResponseEntity<byte[]> options(@PathVariable("path")String path,@RequestHeader Map<String,String> headers);
}
