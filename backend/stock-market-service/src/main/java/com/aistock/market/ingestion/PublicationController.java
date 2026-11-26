package com.aistock.market.ingestion;
import com.aistock.common.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController
public class PublicationController {
    private final PublicationService service;private final String token;
    public PublicationController(PublicationService service,@Value("${aistock.internal.token:}")String token){this.service=service;this.token=token;}
    @PostMapping("/internal/v1/market/versions")
    public ApiResponse<Publication> publish(@RequestBody MarketCandidate body,
            @RequestHeader(value="X-Service-Token",required=false)String supplied,
            @RequestHeader(value="X-Trace-Id",required=false)String trace) {
        if(token.isBlank())throw new BusinessException("INTERNAL_AUTH_NOT_CONFIGURED","内部服务认证未配置",HttpStatus.SERVICE_UNAVAILABLE);
        if(supplied==null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))) throw new BusinessException("INTERNAL_AUTH_REQUIRED","缺少有效服务身份",HttpStatus.UNAUTHORIZED);
        return ApiResponse.success(service.publish(body),TraceIds.normalize(trace));
    }
}
