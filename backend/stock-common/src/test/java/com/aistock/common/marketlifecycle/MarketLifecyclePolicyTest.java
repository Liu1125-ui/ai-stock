package com.aistock.common.marketlifecycle;

import com.aistock.common.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.aistock.common.marketlifecycle.MarketUseRecords.*;

class MarketLifecyclePolicyTest {
    private final UUID cycle = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private State enabled() { return new State(UseState.ENABLED, 0, null); }

    @Test void newUsesOnlyEnterEnabledVersions() {
        assertDoesNotThrow(() -> MarketLifecyclePolicy.requireNewUse(enabled()));
        for (var state : new UseState[]{UseState.DRAINING,UseState.DISABLED}) {
            var e = assertThrows(BusinessException.class, () -> MarketLifecyclePolicy.requireNewUse(new State(state,1,cycle)));
            assertEquals("MARKET_VERSION_USE_BLOCKED",e.code());
        }
    }
    @Test void disableWaitsForUsesAndNeverDisablesCurrentHead() {
        assertEquals(UseState.DISABLED,MarketLifecyclePolicy.disable(enabled(),false,0,cycle).useState());
        var waiting = MarketLifecyclePolicy.disable(enabled(),false,2,cycle);
        assertEquals(UseState.DRAINING,waiting.useState());
        assertEquals(1,waiting.revision());
        assertEquals(cycle,waiting.cycleId());
        assertEquals("MARKET_VERSION_CURRENT_HEAD",assertThrows(BusinessException.class,
            () -> MarketLifecyclePolicy.disable(enabled(),true,0,cycle)).code());
    }
    @Test void drainCompletesOnlyForSameCycleAndNoActiveUses() {
        var waiting = MarketLifecyclePolicy.disable(enabled(),false,1,cycle);
        assertTrue(MarketLifecyclePolicy.finish(waiting,cycle,1).isEmpty());
        assertTrue(MarketLifecyclePolicy.finish(waiting,UUID.randomUUID(),0).isEmpty());
        var disabled = MarketLifecyclePolicy.finish(waiting,cycle,0).orElseThrow();
        assertEquals(UseState.DISABLED,disabled.useState());
        assertEquals(2,disabled.revision());
        assertTrue(MarketLifecyclePolicy.finish(disabled,cycle,0).isEmpty());
    }
    @Test void lateCompletionCannotUndoRestoreOrSecondDisable() {
        var old = MarketLifecyclePolicy.disable(enabled(),false,1,cycle);
        var restored = MarketLifecyclePolicy.restore(old);
        assertEquals(UseState.ENABLED,restored.useState());
        assertNull(restored.cycleId());
        assertTrue(MarketLifecyclePolicy.finish(restored,cycle,0).isEmpty());
        var next = MarketLifecyclePolicy.disable(restored,false,1,UUID.randomUUID());
        assertTrue(MarketLifecyclePolicy.finish(next,cycle,0).isEmpty());
        assertEquals(UseState.ENABLED,MarketLifecyclePolicy.restore(new State(UseState.DISABLED,4,cycle)).useState());
    }
    @Test void rejectsInvalidStatesCountsTransitionsAndOverflow() {
        assertThrows(BusinessException.class, () -> MarketLifecyclePolicy.disable(enabled(),false,-1,cycle));
        assertThrows(BusinessException.class, () -> MarketLifecyclePolicy.disable(enabled(),false,0,null));
        assertThrows(BusinessException.class, () -> MarketLifecyclePolicy.restore(enabled()));
        assertThrows(BusinessException.class, () -> MarketLifecyclePolicy.disable(new State(UseState.DRAINING,1,cycle),false,0,cycle));
        assertThrows(BusinessException.class, () -> MarketLifecyclePolicy.requireNewUse(new State(UseState.ENABLED,1,cycle)));
        assertEquals("MARKET_VERSION_REVISION_EXHAUSTED",assertThrows(BusinessException.class,
            () -> MarketLifecyclePolicy.disable(new State(UseState.ENABLED,Long.MAX_VALUE,null),false,0,cycle)).code());
    }
}
