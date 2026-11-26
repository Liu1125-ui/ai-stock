package com.aistock.scheduler.adminrefresh;
import com.aistock.common.ApiResponse;import com.aistock.common.adminrefresh.AdminMarketRefreshRecords.Context;
import org.springframework.cloud.openfeign.FeignClient;import org.springframework.web.bind.annotation.*;
@FeignClient(name="stock-market-service",contextId="admin-market-refresh-context",url="${aistock.market.url:}")
public interface MarketRefreshContextClient {
 @GetMapping("/internal/v1/market/admin-refresh-context/{instrumentId}")
 ApiResponse<Context> context(@PathVariable("instrumentId")String instrumentId,@RequestHeader("X-Service-Token")String token);
}