package com.aistock.scheduler.task;
import com.aistock.common.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
@FeignClient(name="stock-market-service",contextId="market-receipt",url="${aistock.market.url:}")
public interface MarketReceiptClient {
    @GetMapping("/internal/v1/market/refresh/{operationId}")
    ApiResponse<MarketRefreshClient.Result> get(@PathVariable String operationId,@RequestHeader("X-Service-Token")String token);
}
