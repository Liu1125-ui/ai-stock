package com.aistock.market.quotation;

import com.aistock.market.calendar.CalendarRecords;
import com.aistock.market.calendar.CalendarService;
import com.aistock.market.refresh.IndexCollectorClient;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static com.aistock.market.quotation.IndexSnapshotStore.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IndexOverviewServiceTest {
    private final LocalDate friday = LocalDate.of(2026, 11, 13);
    private final LocalDate monday = friday.plusDays(3);
    private final IndexSnapshotStore store = mock(IndexSnapshotStore.class);
    private final CalendarService calendar = mock(CalendarService.class);
    private final IndexCollectorClient collector = mock(IndexCollectorClient.class);

    @Test void cachedCalendarMarksFridayFreshBeforeMondayCutoffAndStaleAfter() {
        givenSavedFriday();
        givenCalendar(monday.minusDays(1), friday);
        givenCalendar(monday, monday);
        assertEquals("FRESH", service("2026-11-16T11:59:59Z").overview().freshness());
        var late = service("2026-11-16T12:00:00Z").overview();
        assertEquals("STALE", late.freshness());
        assertEquals(monday, late.expectedTradeDate());
        assertEquals(friday, late.snapshot().indices().get(0).date());
        verifyNoInteractions(collector);
    }

    @Test void missingCalendarKeepsSavedNumbersWithUnknownFreshness() {
        givenSavedFriday();
        var result = service("2026-11-16T12:00:00Z").overview();
        assertEquals("UNKNOWN", result.freshness());
        assertEquals(friday, result.snapshot().indices().get(0).date());
        verifyNoInteractions(collector);
    }

    @Test void holidayWithCurrentSnapshotSkipsSourceAndLaterTradingDayPublishes() {
        givenSavedFriday();
        var snapshot = givenCalendar(monday, friday);
        assertFalse(service("2026-11-16T12:10:00Z").refreshDue());
        verifyNoInteractions(collector);
        givenCalendar(monday, monday);
        var request = new Request(monday.minusDays(14), monday);
        var candidate = new Candidate("baostock", request.start(), request.end(), Instant.now(), "hash",
                List.of(new Day(friday, true), new Day(monday, true)), List.of());
        when(collector.collect(request)).thenReturn(candidate);
        assertTrue(service("2026-11-16T12:10:00Z").refreshDue());
        verify(store).publish(candidate);
        verify(calendar, never()).refresh(any());
        assertNotNull(snapshot.versionId());
    }

    @Test void sourceOmittingExpectedTradingDayDoesNotPublish() {
        givenSavedFriday();
        givenCalendar(monday, monday);
        var request = new Request(monday.minusDays(14), monday);
        when(collector.collect(request)).thenReturn(new Candidate("baostock", request.start(), request.end(),
                Instant.now(), "hash", List.of(new Day(friday, true)), List.of()));
        assertThrows(com.aistock.common.BusinessException.class,
                () -> service("2026-11-16T12:10:00Z").refreshDue());
        verify(store, never()).publish(any());
    }

    @Test void scheduledPassReusesCalendarAndKeepsOldDataIfProviderFails() {
        givenSavedFriday();
        var request = new CalendarRecords.Request("index-calendar-" + monday, monday.minusDays(14), monday);
        var publishedCalendar = new CalendarRecords.Snapshot(UUID.randomUUID(), new CalendarRecords.Candidate(
                "baostock", request.start(), request.end(), Instant.EPOCH, "calendar",
                List.of(new CalendarRecords.Day(monday, true))));
        when(calendar.refresh(request)).thenReturn(publishedCalendar);
        var collection = new Request(request.start(), request.end());
        when(collector.collect(collection)).thenThrow(new IllegalStateException("upstream unavailable"));
        new IndexDailyRefresh(service("2026-11-16T12:10:00Z"), Clock.systemUTC()).refresh();
        verify(calendar).refresh(request);
        verify(store, never()).publish(any());
    }

    private IndexOverviewService service(String instant) {
        return new IndexOverviewService(store, calendar, collector, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    private void givenSavedFriday() {
        var index = new IndexView("SSE_COMPOSITE", "sh.000001", friday, "3634", friday.minusDays(1), "3655", "-0.5746");
        when(store.currentWithCoverage()).thenReturn(Optional.of(new CoveredSnapshot(
                new Snapshot(1L, "baostock", Instant.EPOCH, List.of(index)), friday)));
    }

    private CalendarRecords.Snapshot givenCalendar(LocalDate cutoff, LocalDate expected) {
        var candidate = new CalendarRecords.Candidate("baostock", cutoff.minusDays(14), cutoff, Instant.EPOCH,
                "test-calendar", List.of(new CalendarRecords.Day(expected, true), new CalendarRecords.Day(cutoff.plusDays(2), true)));
        var snapshot = new CalendarRecords.Snapshot(UUID.randomUUID(), candidate);
        when(calendar.covering(cutoff.minusDays(14), cutoff)).thenReturn(Optional.of(snapshot));
        return snapshot;
    }
}
