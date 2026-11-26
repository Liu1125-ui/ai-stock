package com.aistock.common;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TraceIdsTest {
    @Test void acceptsSafeIdAndRejectsUntrustedInput() {
        assertEquals("abcDEF123", TraceIds.normalize("abcDEF123"));
        assertNotEquals("<script>", TraceIds.normalize("<script>"));
        assertTrue(TraceIds.normalize("x".repeat(100)).length() <= 64);
    }
}
