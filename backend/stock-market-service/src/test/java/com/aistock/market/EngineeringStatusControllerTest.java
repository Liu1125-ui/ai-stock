package com.aistock.market;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;

class EngineeringStatusControllerTest {
    @Test void declaresScaffoldInsteadOfBusinessReadiness() {
        var response = new EngineeringStatusController().status(new MockHttpServletRequest());
        assertEquals("OK", response.code());
        assertEquals("stock-market-service", response.data().get("component"));
        assertEquals("ENGINEERING_SCAFFOLD", response.data().get("phase"));
        assertNotNull(response.traceId());
    }
}
