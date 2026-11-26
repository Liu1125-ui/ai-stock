package com.aistock.market.quotation;

import com.aistock.common.BusinessException;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "aistock.market.index-refresh-enabled", havingValue = "true")
public class IndexDailyRefresh {
    private static final Logger LOG = LoggerFactory.getLogger(IndexDailyRefresh.class);
    private final IndexOverviewService service;

    private final Clock clock;

    public IndexDailyRefresh(IndexOverviewService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(initialDelay = 90_000)
    public void recoverAfterStartup() {
        var local = LocalTime.now(clock.withZone(ZoneId.of(IndexOverviewService.ZONE)));
        // The regular evening checks already provide recovery during their retry window.
        if (local.isBefore(LocalTime.of(20, 0)) || !local.isBefore(LocalTime.of(20, 51))) {
            refresh();
        }
    }

    @Scheduled(cron = "0 10,30,50 20 * * *", zone = IndexOverviewService.ZONE)
    public void refresh() {
        try {
            if (service.refreshDue()) LOG.info("Market index snapshot updated");
        } catch (BusinessException failure) {
            LOG.warn("Market index refresh did not complete, {}", failure.code());
        } catch (RuntimeException failure) {
            LOG.warn("Market index refresh did not complete, {}", failure.getClass().getSimpleName());
        }
    }
}
