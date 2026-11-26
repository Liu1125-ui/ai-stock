package com.aistock.market.quotation;

import com.aistock.common.BusinessException;
import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PilotLiveQuoteTest {
    private final Instant origin = Instant.parse("2026-11-23T07:36:00Z");
    private final Clock clock = Clock.fixed(origin, ZoneOffset.UTC);
    private final PilotLiveQuote controller = new PilotLiveQuote(clock, "local-token", 30, 20);

    @Test void keepsNewestQuoteWhenCollectorDisconnects() {
        controller.update("local-token", null, input("OPEN", "344.00", -4));
        controller.update("local-token", null, input("OPEN", "343.90", -5));
        assertEquals("LIVE", controller.read(null).getBody().data().state());
        controller.update("local-token", null, input("DISCONNECTED", null, 0));
        var snapshot = controller.read(null).getBody().data();
        assertEquals("DISCONNECTED", snapshot.state());
        assertEquals("344.00", snapshot.price());
        assertEquals(origin.minusSeconds(4), snapshot.quoteTime());
    }

    @Test void distinguishesFreshnessAndMarketClosure() {
        assertEquals("WAITING", controller.read(null).getBody().data().state());
        controller.update("local-token", null, input("OPEN", "344.00", -31));
        assertEquals("STALE", controller.read(null).getBody().data().state());
        controller.update("local-token", null, input("CLOSED", null, 0));
        assertEquals("CLOSED", controller.read(null).getBody().data().state());
    }

    @Test void heartbeatExpiryAndDisabledModeDoNotAdvertiseLivePrices() {
        var mutable = new Clock() {
            Instant instant = origin;
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return instant; }
        };
        var active = new PilotLiveQuote(mutable, "local-token", 30, 20);
        active.update("local-token", null, input("OPEN", "344.00", -4));
        mutable.instant = origin.plusSeconds(21);
        assertEquals("DISCONNECTED", active.read(null).getBody().data().state());
        var disabled = new PilotLiveQuote(clock, "", 30, 20);
        assertEquals("DISABLED", disabled.read(null).getBody().data().state());
        assertThrows(BusinessException.class, () -> disabled.update("", null, input("OPEN", null, 0)));
    }

    @Test void rejectsWrongTokenAndInvalidPayload() {
        assertThrows(BusinessException.class, () -> controller.update("wrong", null, input("OPEN", "344.00", -4)));
        assertThrows(BusinessException.class, () -> controller.update("local-token", null, input("OPEN", "-1", -4)));
        assertThrows(BusinessException.class, () -> controller.update("local-token", null, input("OPEN", "344.00", 30)));
        assertThrows(BusinessException.class, () -> controller.update("local-token", null,
                new PilotLiveQuote.Update("OPEN", "CNY", new BigDecimal("344.00"), origin)));
        assertEquals("WAITING", controller.read(null).getBody().data().state());
    }

    private PilotLiveQuote.Update input(String state, String price, int offset) {
        return new PilotLiveQuote.Update(state, price == null ? null : "HKD",
                price == null ? null : new BigDecimal(price), price == null ? null : origin.plusSeconds(offset));
    }
}
