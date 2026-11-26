package com.aistock.prediction;

import com.aistock.common.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MarketDependencyGuardTest {
    @Test void unknownInstrumentDoesNotOpenCircuit() {
        var guard = new MarketDependencyGuard("test-missing-" + UUID.randomUUID(), 1, 1);
        var request = feign.Request.create(feign.Request.HttpMethod.GET, "/missing", java.util.Map.of(), null, java.nio.charset.StandardCharsets.UTF_8, null);
        assertThrows(feign.FeignException.NotFound.class, () -> guard.invoke(() -> {
            throw new feign.FeignException.NotFound("unknown", request, null, java.util.Map.of());
        }));
        assertEquals("available", guard.invoke(() -> "available"));
    }
    @Test void opensAfterFailureThenPermitsRecovery() throws InterruptedException {
        var guard = new MarketDependencyGuard("test-market-" + UUID.randomUUID(), 1, 1);
        assertThrows(IllegalStateException.class, () -> guard.invoke(() -> {
            throw new IllegalStateException("market offline");
        }));
        var blocked = assertThrows(BusinessException.class, () -> guard.invoke(() -> "fake success"));
        assertEquals("DEPENDENCY_CIRCUIT_OPEN", blocked.code());
        Thread.sleep(1200);
        assertEquals("recovered", guard.invoke(() -> "recovered"));
    }
}
