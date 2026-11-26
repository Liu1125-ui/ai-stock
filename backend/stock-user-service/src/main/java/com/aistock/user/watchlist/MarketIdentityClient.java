package com.aistock.user.watchlist;
import com.aistock.common.ApiResponse;import org.springframework.cloud.openfeign.FeignClient;import org.springframework.web.bind.annotation.*;
@FeignClient(name="stock-market-service",url="${aistock.market.url:}")public interface MarketIdentityClient {
 record InstrumentView(String instrumentId,String code,String name,boolean demoOnly,String sourceService){}
 @GetMapping("/internal/v1/instruments/{id}")ApiResponse<InstrumentView> get(@PathVariable("id")String id,@RequestHeader("X-Service-Token")String token);
}
