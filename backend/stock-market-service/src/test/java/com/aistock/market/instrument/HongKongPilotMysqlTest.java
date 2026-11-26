package com.aistock.market.instrument;

import com.aistock.market.indicator.TechnicalIndicators;
import com.aistock.market.quotation.MarketQueryService;
import com.aistock.market.support.MarketDatabaseFixture;
import com.aistock.market.version.MarketVersionRepository;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("mysql")
class HongKongPilotMysqlTest {
    @Test void tencentIsSearchableWithNoPublishedDailyDataOrForecast() {
        var jdbc = new MarketDatabaseFixture().jdbc();
        var seed = new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V22__hong_kong_pilot_instrument.sql"));
        seed.setSqlScriptEncoding("UTF-8");
        seed.execute(jdbc.getDataSource());
        var instruments = new InstrumentQueryService(jdbc);
        var queries = new MarketQueryService(new MarketVersionRepository(jdbc), instruments,
                new TechnicalIndicators(), Clock.systemUTC(), 12, 20);

        for (var query : List.of("腾讯控股", "00700", "HK00700", "HK.00700", "00700.HK")) {
            var results = queries.search(query, 1, 1).items();
            assertEquals(1, results.size(), query);
            var result = results.get(0);
            assertEquals("HK00700", result.instrumentId(), query);
            assertEquals("UNAVAILABLE", result.forecastCapability(), query);
        }
        assertEquals("腾讯控股", queries.instrument("HK00700").displayName());
        assertEquals("NO_DATA", queries.summaries(List.of("HK00700")).get(0).state());
        assertTrue(queries.daily("HK00700", null, null, "RAW", null).bars().isEmpty());
    }
}
