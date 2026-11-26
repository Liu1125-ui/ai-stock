package com.aistock.user.watchlist;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WatchlistMarketDispatch {
    private final WatchlistMarketQueue queue;
    private final WatchlistMarketScheduler scheduler;
    private final Clock clock;
    private final String token;
    private final boolean enabled;

    public WatchlistMarketDispatch(WatchlistMarketQueue queue, WatchlistMarketScheduler scheduler,
            Clock clock, @Value("${aistock.internal.token:}") String token,
            @Value("${aistock.watchlist.market-preparation-enabled:false}") boolean enabled) {
        this.queue=queue;this.scheduler=scheduler;this.clock=clock;this.token=token;this.enabled=enabled;
    }

    @Scheduled(fixedDelay=30000)
    public void dispatch() {
        if(!enabled||token.isBlank())return;
        var now=clock.instant();
        queue.enqueueActive(now,5);
        for(var request:queue.due(now,5)) {
            try {
                var response=scheduler.submit(new WatchlistMarketScheduler.Job(
                    request.instrumentId(),request.targetDate(),request.key()),token);
                if(response!=null&&"OK".equals(response.code())&&response.data()!=null
                    &&request.instrumentId().equals(response.data().path("instrumentId").asText())
                    &&request.targetDate().toString().equals(response.data().path("targetDate").asText())) {
                    queue.delivered(request);
                    continue;
                }
            } catch(RuntimeException failure) {
                // Retry using the same scheduler key if a response was lost.
            }
            queue.defer(request,clock.instant().plusSeconds(120));
        }
    }
}
