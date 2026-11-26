package com.aistock.prediction;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;

class EngineeringStatusControllerTest {
    @Test void declaresScaffoldInsteadOfBusinessReadiness() {
        var response = new EngineeringStatusController().status(new MockHttpServletRequest());
        assertEquals("OK", response.code());
        assertEquals("stock-prediction-service", response.data().get("component"));
        assertEquals("ENGINEERING_SCAFFOLD", response.data().get("phase"));
        assertNotNull(response.traceId());
    }
}
