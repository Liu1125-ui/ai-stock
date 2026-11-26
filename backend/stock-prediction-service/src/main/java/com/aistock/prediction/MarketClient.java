package com.aistock.prediction;

import com.aistock.common.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "stock-market-service", url = "${aistock.market.url:}")
public interface MarketClient {
    @GetMapping("/internal/v1/instruments/{instrumentId}")
    ApiResponse<MarketInstrument> get(@PathVariable("instrumentId") String instrumentId,
            @RequestHeader("X-Service-Token") String serviceToken,
            @RequestHeader("X-Trace-Id") String traceId);
}
