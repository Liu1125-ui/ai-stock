package com.aistock.common.adminrefresh;

import java.time.*;
import org.junit.jupiter.api.Test;
import static com.aistock.common.adminrefresh.AdminMarketRefreshRecords.*;
import static org.junit.jupiter.api.Assertions.*;

class AdminMarketRefreshPolicyTest {
    private final AdminMarketRefreshPolicy policy=new AdminMarketRefreshPolicy();
    private final Clock before=Clock.fixed(Instant.parse("2026-10-25T11:59:00Z"),ZoneOffset.UTC);
    private final Clock after=Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"),ZoneOffset.UTC);
    private Context context(String start,String end){return new Context("SH600030","baostock","RAW",true,null,
        "00000000-0000-0000-0000-000000000001","0",LocalDate.parse(start),LocalDate.parse(end),before.instant());}
    @Test void appliesShanghaiCutoffEvenWithUtcClock(){
        var c=context("2025-01-01","2026-10-23");
        assertEquals(LocalDate.parse("2026-10-24"),policy.window(c,before).max());
        assertEquals(LocalDate.parse("2026-10-25"),policy.window(c,after).defaultTarget());
        assertEquals(LocalDate.parse("2026-10-23"),policy.window(c,after).min());
    }
    @Test void capsWindowAt1500ElapsedDays(){
        var clock=Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"),ZoneOffset.UTC);
        var c=context("2022-01-01","2022-01-03");
        assertEquals(LocalDate.parse("2026-02-09"),policy.window(c,clock).max());
        assertTrue(policy.admit(c,LocalDate.parse("2026-02-09"),null,clock).allowed());
        assertEquals("REFRESH_RANGE_UNSUPPORTED",policy.admit(c,LocalDate.parse("2026-02-10"),null,clock).reasonCode());
    }
    @Test void refusesRegressionFutureOrNoLegalWindow(){
        var c=context("2025-01-01","2026-10-24");
        assertFalse(policy.admit(c,LocalDate.parse("2026-10-23"),null,before).allowed());
        assertFalse(policy.admit(c,LocalDate.parse("2026-10-25"),null,before).allowed());
        assertFalse(policy.admit(context("2025-01-01","2026-10-25"),LocalDate.parse("2026-10-25"),null,before).allowed());
    }
    @Test void cooldownAllowsAt60SecondsAndNeverBefore(){
        var c=context("2025-01-01","2026-10-24");var target=LocalDate.parse("2026-10-24");
        var no=policy.admit(c,target,before.instant().minusSeconds(59),before);
        assertFalse(no.allowed());assertEquals("REFRESH_COOLDOWN",no.reasonCode());assertEquals(before.instant().plusSeconds(1),no.cooldownUntil());
        assertTrue(policy.admit(c,target,before.instant().minusSeconds(60),before).allowed());
    }
    @Test void refusesBadOrIncompleteContextRatherThanInventingBounds(){
        var c=context("2025-01-01","2026-10-24");
        for(Context bad:new Context[]{null,new Context(c.instrumentId(),"tushare","RAW",true,null,c.baseVersionId(),c.headRevision(),c.coverageStart(),c.coverageEnd(),c.queriedAt()),
          new Context(c.instrumentId(),c.provider(),c.priceBasis(),false,"MARKET_VERSION_UNAVAILABLE",c.baseVersionId(),c.headRevision(),c.coverageStart(),c.coverageEnd(),c.queriedAt()),
          new Context(c.instrumentId(),c.provider(),c.priceBasis(),true,null,c.baseVersionId(),c.headRevision(),null,c.coverageEnd(),c.queriedAt())}) {
            assertFalse(policy.admit(bad,LocalDate.parse("2026-10-24"),null,before).allowed());
            assertNull(policy.window(bad,before).defaultTarget());
        }
    }
    @Test void configurablePolicyChangesBehaviorWithoutWeakeningContextValidation(){
        var custom=new AdminMarketRefreshPolicy(18,100,30);
        var c=context("2026-09-01","2026-10-23");
        assertEquals(LocalDate.parse("2026-10-25"),custom.window(c,before).max());
        assertTrue(custom.admit(c,LocalDate.parse("2026-10-25"),before.instant().minusSeconds(30),before).allowed());
        assertThrows(IllegalArgumentException.class,()->new AdminMarketRefreshPolicy(24,100,30));
        assertThrows(IllegalArgumentException.class,()->new AdminMarketRefreshPolicy(20,0,60));
    }
}