package com.aistock.market.quotation;

import com.aistock.common.BusinessException;
import com.aistock.market.support.MarketDatabaseFixture;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static com.aistock.market.quotation.IndexSnapshotStore.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("mysql")
class IndexSnapshotMysqlTest {
    private final Instant now = Instant.parse("2026-11-16T11:00:00Z");
    private final LocalDate monday = LocalDate.of(2026, 11, 16);

    @Test void savesOneCopyOfIdenticalDataAndNeverMovesBackwards() {
        var jdbc = new MarketDatabaseFixture().jdbc();
        var store = new IndexSnapshotStore(jdbc, Clock.fixed(now, ZoneOffset.UTC));
        var current = candidate(monday, "4100");
        long id = store.publish(current).version();
        assertEquals(id, store.publish(current).version());
        assertEquals("2.5000", store.current().orElseThrow().indices().get(0).changePercent());
        assertNotEquals(id, store.publish(candidate(monday.minusDays(3), "4080")).version());
        assertEquals(id, store.current().orElseThrow().version());
        var delayed = candidate(monday, "4050");
        store.publish(new Candidate(delayed.provider(), delayed.start(), delayed.end(), now.minusSeconds(30),
                delayed.sourcePayloadHash(), delayed.calendar(), delayed.indices()));
        assertEquals(id, store.current().orElseThrow().version());
        assertEquals(3, jdbc.queryForObject("select count(*) from index_market_snapshot", Integer.class));
    }

    @Test void invalidDataNeverReplacesSavedSnapshot() {
        var store = new IndexSnapshotStore(new MarketDatabaseFixture().jdbc(), Clock.fixed(now, ZoneOffset.UTC));
        var good = candidate(monday, "4100");
        long previous = store.publish(good).version();
        assertThrows(BusinessException.class, () -> store.publish(withHash(candidate(monday, "0"))));
        assertThrows(BusinessException.class, () -> store.publish(new Candidate(good.provider(),
                good.start(), good.end(), now, "a".repeat(64), good.calendar(), good.indices())));
        assertEquals(previous, store.current().orElseThrow().version());
    }

    private Candidate candidate(LocalDate latest, String close) {
        var start = latest.equals(monday) ? latest.minusDays(3) : latest.minusDays(1);
        var calendar = start.datesUntil(latest.plusDays(1))
                .map(day -> new Day(day, day.equals(start) || day.equals(latest))).toList();
        var points = List.of(new Point(start, "4000"), new Point(latest, close));
        var indices = List.of(new Series("SSE_COMPOSITE", "sh.000001", points),
                new Series("CSI_300", "sh.000300", points));
        return withHash(new Candidate("baostock", start, latest, now, null, calendar, indices));
    }

    private Candidate withHash(Candidate c) {
        return new Candidate(c.provider(), c.start(), c.end(), c.fetchedAt(),
                fingerprint(c.calendar(), c.indices()), c.calendar(), c.indices());
    }
}
