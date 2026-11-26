package com.aistock.market.quotation;

import com.aistock.market.forecast.ForecastInputService;
import com.aistock.market.indicator.TechnicalIndicators;
import com.aistock.market.instrument.InstrumentQueryService;
import com.aistock.market.version.MarketVersionRepository;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForecastCapabilitySemanticsTest {
    @Test void detailAndSearchDoNotClaimPublishedModelsAreDisconnected() {
        var repository = mock(MarketVersionRepository.class);
        when(repository.jdbc()).thenReturn(mock(JdbcTemplate.class));
        var instruments = mock(InstrumentQueryService.class);
        var instrument = new InstrumentQueryService.Instrument("SH600030", "600030", "中信证券", true, "ACTIVE");
        when(instruments.get("SH600030")).thenReturn(instrument);
        when(instruments.search("600030",1,10)).thenReturn(new InstrumentQueryService.Page(List.of(instrument),1,10,1));
        var service = new MarketQueryService(repository, instruments, new TechnicalIndicators(),
            Clock.systemUTC(), 12, 20, mock(ForecastInputService.class));
        for (var detail : List.of(service.instrument("SH600030"),service.search("600030",1,10).items().get(0))) {
            assertEquals("NOT_EVALUATED",detail.forecastCapability());
            assertEquals("预测能力以已发布模型目录和输入检查结果为准",detail.forecastReason());
            assertEquals("NO_DATA",detail.summary().state());
        }
    }
}
