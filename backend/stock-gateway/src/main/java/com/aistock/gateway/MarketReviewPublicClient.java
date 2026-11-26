package com.aistock.gateway;
import java.util.Map;import org.springframework.cloud.openfeign.FeignClient;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;
@FeignClient(name="stock-market-service",contextId="market-review-public",url="${aistock.market.url:}",configuration=MarketReferenceFeignConfiguration.class)
public interface MarketReviewPublicClient {
 @GetMapping("/api/v1/{path}") ResponseEntity<byte[]> get(@PathVariable("path")String path,@RequestHeader Map<String,String> headers);
 @PostMapping(value="/api/v1/{path}",consumes="application/json") ResponseEntity<byte[]> post(@PathVariable("path")String path,@RequestBody byte[] body,@RequestHeader Map<String,String> headers);
}
