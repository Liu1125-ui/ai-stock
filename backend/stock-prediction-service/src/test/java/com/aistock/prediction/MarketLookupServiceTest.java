package com.aistock.prediction;

import com.aistock.common.ApiResponse;
import com.aistock.common.BusinessException;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MarketLookupServiceTest {
    private final MarketClient client = mock(MarketClient.class);
    private final MarketLookupService service = new MarketLookupService(client, "service-secret",
            new MarketDependencyGuard("lookup-test-" + java.util.UUID.randomUUID(), 3, 1));

    @Test void returnsDataSuppliedByMarketService() {
        var instrument = new MarketInstrument("DEMO-001", "DEMO.XX", "联调样本", true,
                "stock-market-service");
        when(client.get("DEMO-001", "service-secret", "trace-1234"))
                .thenReturn(ApiResponse.success(instrument, "trace-1234"));
        var result = service.lookup("DEMO-001", "trace-1234");
        assertEquals("DEMO.XX", result.code());
        assertEquals("stock-market-service", result.sourceService());
    }

    @Test void translatesMarketFailureWithoutInventingAnInstrument() {
        when(client.get("DEMO-001", "service-secret", "trace-1234"))
                .thenThrow(mock(FeignException.ServiceUnavailable.class));
        var error = assertThrows(BusinessException.class,
                () -> service.lookup("DEMO-001", "trace-1234"));
        assertEquals("DEPENDENCY_UNAVAILABLE", error.code());
        assertNull(error.data());
    }

    @Test void translatesDiscoveryFailureWithoutInventingAnInstrument() {
        when(client.get("DEMO-001", "service-secret", "trace-1234"))
                .thenThrow(new IllegalStateException("no market instances"));
        var error = assertThrows(BusinessException.class,
                () -> service.lookup("DEMO-001", "trace-1234"));
        assertEquals("DEPENDENCY_UNAVAILABLE", error.code());
    }
}
