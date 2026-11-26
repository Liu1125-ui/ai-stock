package com.aistock.gateway;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@FeignClient(name="stock-user-service",url="${aistock.user.url:}")
public interface UserPublicClient {
    @GetMapping("/api/v1/{path}") ResponseEntity<byte[]> get(@PathVariable("path")String path,@RequestParam Map<String,String> query,@RequestHeader Map<String,String> headers);
    @PostMapping("/api/v1/{path}") ResponseEntity<byte[]> post(@PathVariable("path")String path,@RequestParam Map<String,String> query,@RequestHeader Map<String,String> headers,@RequestBody byte[] body);
    @PatchMapping("/api/v1/{path}") ResponseEntity<byte[]> patch(@PathVariable("path")String path,@RequestParam Map<String,String> query,@RequestHeader Map<String,String> headers,@RequestBody byte[] body);
    @PutMapping("/api/v1/{path}") ResponseEntity<byte[]> put(@PathVariable("path")String path,@RequestParam Map<String,String> query,@RequestHeader Map<String,String> headers,@RequestBody byte[] body);
    @DeleteMapping("/api/v1/{path}") ResponseEntity<byte[]> delete(@PathVariable("path")String path,@RequestParam Map<String,String> query,@RequestHeader Map<String,String> headers);
    @DeleteMapping("/api/v1/{path}") ResponseEntity<byte[]> deleteWithBody(@PathVariable("path")String path,@RequestParam Map<String,String> query,@RequestHeader Map<String,String> headers,@RequestBody byte[] body);
    @RequestMapping(value="/api/v1/{path}",method=RequestMethod.OPTIONS) ResponseEntity<byte[]> options(@PathVariable("path")String path,@RequestHeader Map<String,String> headers);
}
