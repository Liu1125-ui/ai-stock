package com.aistock.gateway;
import java.util.Map;import org.springframework.cloud.openfeign.FeignClient;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;
@FeignClient(name="stock-market-service",contextId="market-reference-public",url="${aistock.market.url:}",configuration=MarketReferenceFeignConfiguration.class)
public interface MarketReferencePublicClient {
 @GetMapping("/api/v1/admin/market-data/{instrumentId}/versions/{versionId}/references") ResponseEntity<byte[]> inspect(@PathVariable("instrumentId")String stock,@PathVariable("versionId")String version,@RequestHeader Map<String,String> headers);
}
