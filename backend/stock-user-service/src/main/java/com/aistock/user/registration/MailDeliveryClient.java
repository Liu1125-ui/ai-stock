package com.aistock.user.registration;

import com.aistock.common.ApiResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name="stock-notification-service",url="${aistock.notification.url:}")
public interface MailDeliveryClient {
    record Command(UUID requestId, UUID challengeId, String email, String code, Instant expiresAt) {
        @Override public String toString() { return "MailCommand[requestId=" + requestId + "]"; }
    }
    record DeliveryView(UUID requestId, String state, Instant expiresAt) { }
    @PostMapping("/internal/v1/email-deliveries")
    ApiResponse<DeliveryView> accept(@RequestBody Command command, @RequestHeader("X-Service-Token") String token);
    @GetMapping("/internal/v1/email-deliveries/{requestId}")
    ApiResponse<DeliveryView> find(@PathVariable("requestId") UUID id, @RequestHeader("X-Service-Token") String token);
}
