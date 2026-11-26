package com.aistock.market;

import com.aistock.common.ApiResponse;
import com.aistock.common.BusinessException;
import com.aistock.common.TraceIds;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/instruments")
public class MarketInstrumentController {
    private final InstrumentRepository repository;
    private final String serviceToken;

    public MarketInstrumentController(InstrumentRepository repository,
            @Value("${aistock.internal.token:}") String serviceToken) {
        this.repository = repository;
        this.serviceToken = serviceToken;
    }

    @GetMapping("/{instrumentId}")
    public ApiResponse<InstrumentView> get(@PathVariable String instrumentId,
            @RequestHeader(value = "X-Service-Token", required = false) String suppliedToken,
            @RequestHeader(value = "X-Trace-Id", required = false) String suppliedTraceId) {
        if (serviceToken.isBlank()) {
            throw new BusinessException("INTERNAL_AUTH_NOT_CONFIGURED", "内部服务认证未配置", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (suppliedToken == null || !MessageDigest.isEqual(
                serviceToken.getBytes(StandardCharsets.UTF_8), suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException("INTERNAL_AUTH_REQUIRED", "缺少有效服务身份", HttpStatus.UNAUTHORIZED);
        }
        var instrument = repository.findById(instrumentId)
                .orElseThrow(() -> new BusinessException("INSTRUMENT_NOT_FOUND", "证券不存在", HttpStatus.NOT_FOUND));
        return ApiResponse.success(instrument, TraceIds.normalize(suppliedTraceId));
    }
}
