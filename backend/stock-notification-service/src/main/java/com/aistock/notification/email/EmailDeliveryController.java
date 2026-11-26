package com.aistock.notification.email;

import com.aistock.common.ApiResponse;
import com.aistock.common.BusinessException;
import com.aistock.common.TraceIds;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/email-deliveries")
public class EmailDeliveryController {
    private final EmailDeliveryService service;
    private final String token;
    public EmailDeliveryController(EmailDeliveryService service, @Value("${aistock.internal.token:}") String token) { this.service = service; this.token = token; }
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<DeliveryView> accept(@RequestBody EmailCommand command,
            @RequestHeader(value="X-Service-Token",required=false) String supplied,
            @RequestHeader(value="X-Trace-Id",required=false) String trace) {
        authenticate(supplied); return ApiResponse.success(service.accept(command), TraceIds.normalize(trace));
    }
    @GetMapping("/{requestId}")
    public ApiResponse<DeliveryView> find(@PathVariable UUID requestId,
            @RequestHeader(value="X-Service-Token",required=false) String supplied,
            @RequestHeader(value="X-Trace-Id",required=false) String trace) {
        authenticate(supplied); return ApiResponse.success(service.find(requestId), TraceIds.normalize(trace));
    }
    private void authenticate(String supplied) {
        if (token.isBlank()) throw new BusinessException("INTERNAL_AUTH_NOT_CONFIGURED", "内部服务认证未配置", HttpStatus.SERVICE_UNAVAILABLE);
        if (supplied == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)))
            throw new BusinessException("INTERNAL_AUTH_REQUIRED", "缺少有效服务身份", HttpStatus.UNAUTHORIZED);
    }
}
