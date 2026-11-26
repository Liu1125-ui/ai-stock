package com.aistock.user.watchlist;

import com.aistock.common.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name="stock-scheduler-service",url="${aistock.watchlist.scheduler-url:}")
public interface WatchlistMarketScheduler {
    @PostMapping("/internal/v1/tasks")
    ApiResponse<JsonNode> submit(@RequestBody Job job,@RequestHeader("X-Service-Token") String token);

    record Job(String instrumentId, LocalDate targetDate, String key) {}
}
