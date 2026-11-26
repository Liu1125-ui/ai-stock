package com.aistock.market.instrument;

import com.aistock.market.support.MarketDatabaseFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("mysql")
class InstrumentSearchMysqlTest {
    @Test void ranksExactThenPrefixBeforePaginationAndAcceptsBothCodeForms() {
        var jdbc = new MarketDatabaseFixture().jdbc();
        jdbc.update("INSERT INTO instrument(instrument_id,ticker,display_name,demo_only) VALUES (?, ?, ?, false), (?, ?, ?, false), (?, ?, ?, false)",
            "SH600001", "SH600001", "甲贵州茅台", "SH600002", "SH600002", "贵州茅台相关", "SH600519", "SH600519", "贵州茅台");
        var search = new InstrumentQueryService(jdbc);
        for (var term : java.util.List.of("贵州茅台", "600519", "SH600519", "sh600519"))
            assertEquals("SH600519", search.search(term, 1, 1).items().get(0).instrumentId(), term);
        assertEquals("SH600002", search.search("贵州茅台", 2, 1).items().get(0).instrumentId());
        assertEquals(3, search.search("茅台", 1, 1).total());
        assertEquals(0, search.search("%", 1, 1).total());
    }
}
