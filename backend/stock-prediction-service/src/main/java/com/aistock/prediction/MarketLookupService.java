package com.aistock.prediction;

import com.aistock.common.BusinessException;
import com.aistock.common.ApiResponse;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MarketLookupService {
    private final MarketClient client;
    private final String serviceToken;
    private final MarketDependencyGuard guard;

    public MarketLookupService(MarketClient client,
            @Value("${aistock.internal.token:}") String serviceToken, MarketDependencyGuard guard) {
        this.client = client;
        this.serviceToken = serviceToken;
        this.guard = guard;
    }

    public MarketInstrument lookup(String instrumentId, String traceId) {
        if (serviceToken.isBlank()) {
            throw new BusinessException("INTERNAL_AUTH_NOT_CONFIGURED", "内部服务认证未配置", HttpStatus.SERVICE_UNAVAILABLE);
        }
        ApiResponse<MarketInstrument> response;
        try {
            response = guard.invoke(() -> client.get(instrumentId, serviceToken, traceId));
        } catch (BusinessException error) {
            throw error;
        } catch (FeignException.NotFound error) {
            throw new BusinessException("INSTRUMENT_NOT_FOUND", "证券不存在", HttpStatus.NOT_FOUND);
        } catch (RuntimeException error) {
            throw new BusinessException("DEPENDENCY_UNAVAILABLE", "行情服务暂时不可用", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (response == null || !"OK".equals(response.code()) || response.data() == null) {
            throw new BusinessException("DEPENDENCY_INVALID_RESPONSE", "行情服务返回无效数据", HttpStatus.BAD_GATEWAY);
        }
        return response.data();
    }
}
