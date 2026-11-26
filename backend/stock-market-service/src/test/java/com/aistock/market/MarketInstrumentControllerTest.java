package com.aistock.market;

import com.aistock.common.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MarketInstrumentControllerTest {
    private final InstrumentRepository repository = mock(InstrumentRepository.class);
    private final MarketInstrumentController controller = new MarketInstrumentController(repository, "service-secret");

    @Test void rejectsMissingServiceCredentialBeforeReadingData() {
        var error = assertThrows(BusinessException.class,
                () -> controller.get("DEMO-001", null, "trace-1234"));
        assertEquals("INTERNAL_AUTH_REQUIRED", error.code());
        verifyNoInteractions(repository);
    }

    @Test void returnsMarkedFixtureFromMarketRepository() {
        when(repository.findById("DEMO-001"))
                .thenReturn(Optional.of(new InstrumentView("DEMO-001", "DEMO.XX", "联调样本", true)));
        var response = controller.get("DEMO-001", "service-secret", "trace-1234");
        assertEquals("DEMO.XX", response.data().code());
        assertTrue(response.data().demoOnly());
        assertEquals("stock-market-service", response.data().sourceService());
        assertEquals("trace-1234", response.traceId());
    }
}
