package com.aistock.market.quotation;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import java.time.ZonedDateTime;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class IndexDailyRefreshRegistrationTest {
    @Test void enabledJobRegistersThreeEveningSlotsInShanghai() throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                    Map.of("aistock.market.index-refresh-enabled", "true")));
            context.registerBean(IndexOverviewService.class, () -> mock(IndexOverviewService.class));
            context.registerBean(java.time.Clock.class, java.time.Clock::systemUTC);
            context.register(IndexDailyRefresh.class);
            context.refresh();
            var tasks = context.getBean(ScheduledTaskHolder.class).getScheduledTasks();
            assertEquals(2, tasks.size());
            var configured = IndexDailyRefresh.class.getMethod("refresh").getAnnotation(Scheduled.class);
            assertEquals("Asia/Shanghai", configured.zone());
            var cron = org.springframework.scheduling.support.CronExpression.parse(configured.cron());
            var start = ZonedDateTime.parse("2026-11-16T20:00:00+08:00[Asia/Shanghai]");
            var first = cron.next(start);
            var second = cron.next(first);
            var third = cron.next(second);
            assertEquals(start.plusMinutes(10), first);
            assertEquals(start.plusMinutes(30), second);
            assertEquals(start.plusMinutes(50), third);
            assertEquals(start.plusDays(1).plusMinutes(10), cron.next(third));
        }
    }
    @Test void startupRecoveryRunsOutsideEveningRetryWindow() {
        var service = mock(IndexOverviewService.class);
        var utc = java.time.ZoneOffset.UTC;
        var afternoon = java.time.Clock.fixed(java.time.Instant.parse("2026-11-17T08:00:00Z"), utc);
        new IndexDailyRefresh(service, afternoon).recoverAfterStartup();
        org.mockito.Mockito.verify(service).refreshDue();
        var retryWindow = java.time.Clock.fixed(java.time.Instant.parse("2026-11-17T12:20:00Z"), utc);
        new IndexDailyRefresh(service, retryWindow).recoverAfterStartup();
        org.mockito.Mockito.verifyNoMoreInteractions(service);
    }
}
