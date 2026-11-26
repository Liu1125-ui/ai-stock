package com.aistock.market.quotation;

import com.aistock.common.BusinessException;
import com.aistock.market.instrument.InstrumentQueryService;
import com.aistock.market.support.MarketDatabaseFixture;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("mysql")
class StockValuationMysqlTest {
    @Test void versionAndTradingDateGateTheFieldsAndPreserveNegativeEarnings() {
        var fixture = new MarketDatabaseFixture();
        var version = new MarketQueryMysqlTest().publish(fixture, List.of());
        var store = new StockValuationStore(fixture.jdbc(), new InstrumentQueryService(fixture.jdbc()));
        var target = store.target("SH600030", version);
        var now = Instant.parse("2026-09-24T13:00:00Z");
        assertEquals(LocalDate.parse("2026-09-24"), target.tradeDate());
        assertEquals("NO_DATA", store.read("SH600030", version).state());
        assertFalse(store.save(target, new StockValuationStore.Sample(target.tradeDate().minusDays(1), "25", "3"), now));
        assertFalse(store.save(target, new StockValuationStore.Sample(target.tradeDate(), "0", "NaN"), now));
        assertTrue(store.save(target, new StockValuationStore.Sample(target.tradeDate(), "-19.2500", "0"), now));
        assertTrue(store.save(target, new StockValuationStore.Sample(target.tradeDate(), "90", "2"), now.minusSeconds(60)));
        var view = store.read("SH600030", version);
        assertEquals("AVAILABLE", view.state());
        assertEquals("-19.25", view.peTtm());
        assertNull(view.pbMrq());
        assertEquals("baostock", view.provider());
        fixture.jdbc().update("insert into instrument(instrument_id,ticker,display_name,demo_only) values ('SZ000002','SZ000002','示例',false)");
        assertThrows(BusinessException.class, () -> store.read("SZ000002", version));
        fixture.jdbc().update("update market_daily_bar set trade_status=0 where version_id=? and trade_date=?", version, target.tradeDate());
        assertEquals("NO_DATA", store.read("SH600030", version).state());
    }
}
