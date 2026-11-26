package com.aistock.user.watchlist;

import com.aistock.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class WatchlistMarketDispatchTest {
    @Test void retriesDeliveryWithSameDailyKeyWhenSchedulerWasUnreachable() {
        var queue=mock(WatchlistMarketQueue.class);
        var scheduler=mock(WatchlistMarketScheduler.class);
        var now=Instant.parse("2026-11-20T06:00:00Z");
        var date=LocalDate.parse("2026-11-19");
        var request=new WatchlistMarketQueue.Request("SH600519",date);
        var job=new WatchlistMarketScheduler.Job("SH600519",date,"daily-SH600519-2026-11-19");
        when(queue.due(now,5)).thenReturn(List.of(request));
        var receipt=new ObjectMapper().createObjectNode()
            .put("instrumentId","SH600519").put("targetDate",date.toString());
        when(scheduler.submit(job,"test-token")).thenThrow(new IllegalStateException("transient"))
            .thenReturn(ApiResponse.success(receipt,"test"));
        var worker=new WatchlistMarketDispatch(queue,scheduler,Clock.fixed(now,ZoneOffset.UTC),"test-token",true);
        worker.dispatch();
        verify(queue).defer(request,now.plusSeconds(120));
        verify(queue,never()).delivered(request);
        worker.dispatch();
        verify(scheduler,times(2)).submit(job,"test-token");
        verify(queue).delivered(request);
    }
}
