package com.aistock.market.quotation;
import com.aistock.common.BusinessException;
import com.aistock.market.ingestion.*;
import com.aistock.market.indicator.TechnicalIndicators;
import com.aistock.market.instrument.InstrumentQueryService;
import com.aistock.market.support.MarketDatabaseFixture;
import com.aistock.market.version.MarketVersionRepository;
import java.time.*;import java.math.BigDecimal;import java.util.*;
import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;
@Tag("mysql") class MarketQueryMysqlTest {
    final Clock clock=Clock.fixed(Instant.parse("2026-09-24T13:00:00Z"),ZoneOffset.UTC);
    MarketQueryService service(MarketDatabaseFixture f){return new MarketQueryService(new MarketVersionRepository(f.jdbc()),new InstrumentQueryService(f.jdbc()),new TechnicalIndicators(),clock,12,20);}
    String publish(MarketDatabaseFixture f,List<TradingDay> calendar){var rows=new ArrayList<RawBar>();for(int i=0;i<100;i++){var p=BigDecimal.valueOf(10+i);rows.add(new RawBar(LocalDate.of(2026,6,17).plusDays(i),p,p,p,p,BigDecimal.TEN,BigDecimal.TEN,1,null));}
      return new PublicationService(new MarketVersionRepository(f.jdbc()),new CandidateValidator()).publish(new MarketCandidate("seed","SH600030","SH600030","SH600030",false,"UNKNOWN","baostock","RAW",clock.instant(),"source","a".repeat(64),rows,calendar,0L)).versionId();}
    @Test void realSearchExcludesDemoAndTreatsWildcardsLiterally(){var f=new MarketDatabaseFixture();publish(f,List.of());var s=service(f);assertEquals(1,s.search("600030",1,20).total());assertEquals(0,s.search("%",1,20).total());assertEquals(0,s.search("_",1,20).total());assertEquals(0,s.search("DEMO",1,20).total());}
    @Test void rangesShareIndicatorsAndDecimalsRemainStrings(){var f=new MarketDatabaseFixture();publish(f,List.of());var s=service(f);var a=s.daily("SH600030","2026-07-01","2026-09-24","RAW",null);var b=s.daily("SH600030","2026-06-17","2026-09-24","RAW",null);assertEquals(b.bars().get(99),a.bars().get(a.bars().size()-1));assertEquals("109.000000",a.bars().get(a.bars().size()-1).close());assertEquals("UNKNOWN",a.freshness());assertEquals("tech-v1",a.formulaVersion());}
    @Test void invalidRangesVersionsAndBasisAreControlled(){var f=new MarketDatabaseFixture();var v=publish(f,List.of());var s=service(f);assertThrows(BusinessException.class,()->s.daily("SH600030","2026-09-25",null,"RAW",null));assertThrows(BusinessException.class,()->s.daily("SH600030","2025-01-01","2026-09-24","RAW",null));assertThrows(BusinessException.class,()->s.daily("SH600030",null,null,"QFQ",null));assertEquals("RESOURCE_NOT_FOUND",assertThrows(BusinessException.class,()->s.daily("SZ000002",null,null,"RAW",v)).code());assertThrows(BusinessException.class,()->s.summaries(java.util.stream.IntStream.range(0,101).mapToObj(i->"SH"+String.format("%06d",i)).toList()));}
    @Test void missingHeadAndEmptyRangeAreDistinct(){var f=new MarketDatabaseFixture();publish(f,List.of());f.jdbc().update("INSERT INTO instrument(instrument_id,ticker,display_name,demo_only) VALUES ('SZ000002','SZ000002','SZ000002',false)");var s=service(f);assertEquals("NO_DATA",s.instrument("SZ000002").summary().state());assertEquals("EMPTY",s.daily("SH600030","2026-01-01","2026-01-02","RAW",null).state());assertEquals(2,s.summaries(List.of("SH600030","SZ000002","SH600030")).size());}
    @Test void freshnessRequiresContinuousCurrentCalendarAndCompleteRows(){var f=new MarketDatabaseFixture();var cal=new ArrayList<TradingDay>();for(int i=0;i<100;i++)cal.add(new TradingDay(LocalDate.of(2026,6,17).plusDays(i),true));publish(f,cal);assertEquals("FRESH",service(f).daily("SH600030",null,null,"RAW",null).freshness());var later=new MarketQueryService(new MarketVersionRepository(f.jdbc()),new InstrumentQueryService(f.jdbc()),new TechnicalIndicators(),Clock.fixed(Instant.parse("2026-09-25T13:00:00Z"),ZoneOffset.UTC),12,20);assertEquals("UNKNOWN",later.daily("SH600030",null,null,"RAW",null).freshness());f.jdbc().update("DELETE FROM market_daily_bar WHERE trade_date='2026-09-23'");assertEquals("STALE",service(f).daily("SH600030",null,null,"RAW",null).freshness());}
    @Test void batchSummariesReturnVerifiedMetadataEvenWithoutPrices() {
        var f=new MarketDatabaseFixture();publish(f,List.of());
        f.jdbc().update("update instrument set display_name='中信证券',name_verified=true,listing_status='ACTIVE' where instrument_id='SH600030'");
        f.jdbc().update("insert into instrument(instrument_id,ticker,display_name,demo_only,name_verified,listing_status) values ('SZ000002','SZ000002','万科A',false,true,'ACTIVE')");
        var result=service(f).summaries(List.of("SH600030","SZ000002"));
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var a=mapper.valueToTree(result.get(0));var b=mapper.valueToTree(result.get(1));
        assertEquals("中信证券",a.path("displayName").asText());assertTrue(a.path("displayNameVerified").asBoolean());
        assertEquals("ACTIVE",a.path("listingStatus").asText());
        assertEquals("万科A",b.path("displayName").asText());assertTrue(b.path("displayNameVerified").asBoolean());
        assertEquals("NO_DATA",b.path("state").asText());
        var unknown=service(f).summaries(List.of("SH999999")).get(0);assertEquals("UNAVAILABLE",unknown.state());
        var missing=mapper.valueToTree(unknown);assertTrue(missing.path("displayName").isNull());assertFalse(missing.path("displayNameVerified").asBoolean());assertEquals("UNKNOWN",missing.path("listingStatus").asText());
    }
    @Test void dailyChangeRequiresTwoAdjacentTradingSessions(){
        var f=new MarketDatabaseFixture();
        var cal=new ArrayList<TradingDay>();
        for(int i=0;i<100;i++)cal.add(new TradingDay(LocalDate.of(2026,6,17).plusDays(i),true));
        publish(f,cal);
        var s=service(f);
        assertEquals("0.9259",s.summaries(List.of("SH600030")).get(0).dailyChangePercent());
        f.jdbc().update("delete from market_daily_bar where trade_date='2026-09-23'");
        assertNull(s.summaries(List.of("SH600030")).get(0).dailyChangePercent());
    }
}
