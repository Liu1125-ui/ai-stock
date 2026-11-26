package com.aistock.scheduler.task;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class MarketUseTaskErrorsTest {
 @Test void unknownTransportAndReceiptErrorsNeverBecomeFabricatedTerminal(){for(var code:java.util.List.of("MARKET_USE_UNAVAILABLE","MARKET_USE_RESPONSE_INVALID","INTERNAL_ERROR","MARKET_VERSION_USE_BLOCKED"))assertFalse(MarketUseTaskErrors.definitive(500,code));assertFalse(MarketUseTaskErrors.definitive(409,"MARKET_USE_UNAVAILABLE"));assertFalse(MarketUseTaskErrors.definitive(200,"MARKET_VERSION_USE_BLOCKED"));assertFalse(MarketUseTaskErrors.definitive(-1,"MARKET_VERSION_USE_BLOCKED"));assertTrue(MarketUseTaskErrors.definitive(503,"MARKET_USE_NOT_READY"));assertTrue(MarketUseTaskErrors.definitive(409,"MARKET_VERSION_USE_BLOCKED"));assertEquals("MARKET_VERSION_USE_BLOCKED",com.aistock.scheduler.admin.AdminTaskReasons.safe("MARKET_VERSION_USE_BLOCKED").code());}
}
