package com.aistock.notification.alert;
import com.aistock.common.ApiResponse;import org.springframework.cloud.openfeign.FeignClient;import org.springframework.web.bind.annotation.*;
@FeignClient(name="stock-market-service",contextId="notificationMarket",url="${aistock.market.url:}",configuration=NotificationClientConfiguration.class)
public interface AlertBoundaryClient {
 @GetMapping("/internal/v1/market/alert-boundary/{instrument}") ApiResponse<AlertRecords.Boundary> get(@PathVariable("instrument")String instrument,@RequestHeader("X-Service-Token")String token);
}
