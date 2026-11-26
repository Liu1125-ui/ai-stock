package com.aistock.gateway;
import java.util.Map;import org.springframework.cloud.openfeign.FeignClient;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;
@FeignClient(name="stock-market-service",url="${aistock.market.url:}")public interface MarketPublicClient {
 @GetMapping("/api/v1/{path}")ResponseEntity<byte[]> get(@PathVariable("path")String path,@RequestParam Map<String,String> query,@RequestHeader Map<String,String> headers);
}
