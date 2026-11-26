package com.aistock.market.quotation;

import com.aistock.market.calendar.CalendarRecords;
import com.aistock.market.calendar.CalendarService;
import com.aistock.market.refresh.IndexCollectorClient;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class IndexOverviewService {
    public record Overview(String freshness, LocalDate expectedTradeDate, IndexSnapshotStore.Snapshot snapshot) {}

    static final String ZONE = "Asia/Shanghai";
    private static final int CUTOFF_HOUR = 20;
    private static final int LOOKBACK_DAYS = 14;
    private final IndexSnapshotStore store;
    private final CalendarService calendar;
    private final IndexCollectorClient collector;
    private final Clock clock;

    public IndexOverviewService(IndexSnapshotStore store, CalendarService calendar, IndexCollectorClient collector, Clock clock) {
        this.store = store;
        this.calendar = calendar;
        this.collector = collector;
        this.clock = clock;
    }

    public Overview overview() {
        LocalDate cutoff = cutoff();
        var current = store.currentWithCoverage();
        var expected = cachedCalendar(cutoff).flatMap(snapshot -> lastTradingDay(snapshot, cutoff));
        if (expected.isEmpty()) {
            expected = current.filter(saved -> covers(saved, cutoff)).map(IndexOverviewService::tradeDate);
        }
        LocalDate expectedDate = expected.orElse(null);
        if (current.isEmpty()) return new Overview("NO_DATA", expectedDate, null);
        var saved = current.orElseThrow();
        LocalDate actual = tradeDate(saved);
        String freshness = "UNKNOWN";
        if (expectedDate != null) {
            if (actual.equals(expectedDate)) freshness = "FRESH";
            if (actual.isBefore(expectedDate)) freshness = "STALE";
        }
        return new Overview(freshness, expectedDate, saved.snapshot());
    }

    synchronized boolean refreshDue() {
        LocalDate cutoff = cutoff();
        var current = store.currentWithCoverage();
        var savedCalendar = cachedCalendar(cutoff);
        if (savedCalendar.isEmpty() && current.filter(saved -> covers(saved, cutoff)).isPresent()) {
            return false;
        }
        var verified = savedCalendar.orElseGet(() -> calendar.refresh(new CalendarRecords.Request(
                "index-calendar-" + cutoff, cutoff.minusDays(LOOKBACK_DAYS), cutoff)));
        var expected = lastTradingDay(verified, cutoff);
        if (expected.isEmpty()
                || current.filter(saved -> !tradeDate(saved).isBefore(expected.orElseThrow())).isPresent()) {
            return false;
        }
        var request = new IndexSnapshotStore.Request(cutoff.minusDays(LOOKBACK_DAYS), cutoff);
        var candidate = collector.collect(request);
        if (candidate == null || !request.start().equals(candidate.start()) || !request.end().equals(candidate.end())
                || candidate.calendar() == null || !expected.equals(candidate.calendar().stream()
                        .filter(Objects::nonNull).filter(day -> Boolean.TRUE.equals(day.open()))
                        .map(IndexSnapshotStore.Day::date).filter(Objects::nonNull).max(LocalDate::compareTo))) {
            throw IndexSnapshotStore.invalid();
        }
        store.publish(candidate);
        return true;
    }

    private LocalDate cutoff() {
        var local = clock.instant().atZone(ZoneId.of(ZONE));
        return local.toLocalDate().minusDays(local.getHour() < CUTOFF_HOUR ? 1 : 0);
    }

    private Optional<CalendarRecords.Snapshot> cachedCalendar(LocalDate cutoff) {
        return calendar.covering(cutoff.minusDays(LOOKBACK_DAYS), cutoff);
    }

    private static Optional<LocalDate> lastTradingDay(CalendarRecords.Snapshot snapshot, LocalDate cutoff) {
        return snapshot.candidate().days().stream().filter(day -> Boolean.TRUE.equals(day.open()))
                .map(CalendarRecords.Day::date).filter(day -> !day.isAfter(cutoff)).max(LocalDate::compareTo);
    }

    private static boolean covers(IndexSnapshotStore.CoveredSnapshot saved, LocalDate cutoff) {
        return !saved.checkedThrough().isBefore(cutoff) && !tradeDate(saved).isAfter(cutoff);
    }

    private static LocalDate tradeDate(IndexSnapshotStore.CoveredSnapshot saved) {
        return saved.snapshot().indices().get(0).date();
    }
}
