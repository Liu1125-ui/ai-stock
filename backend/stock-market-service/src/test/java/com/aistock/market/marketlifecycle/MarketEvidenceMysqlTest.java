package com.aistock.market.marketlifecycle;

import com.aistock.common.BusinessException;
import com.aistock.common.marketlifecycle.MarketUseRecords.*;
import com.aistock.market.forecast.*;
import com.aistock.market.ingestion.*;
import com.aistock.market.quotation.*;
import com.aistock.market.support.MarketDatabaseFixture;
import com.aistock.market.version.MarketVersionRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("mysql")
class MarketEvidenceMysqlTest {
    private MarketDatabaseFixture f;private MarketLifecycleService uses;private MarketVersionRepository market;private String old,head;
    private final Clock clock=Clock.fixed(Instant.parse("2026-11-04T06:00:00Z"),ZoneOffset.UTC);
    private final String actor="00000000-0000-0000-0000-000000000001";
    private List<RawBar> bars(String price){var result=new ArrayList<RawBar>();var p=new BigDecimal(price);for(int i=59;i>=0;i--)result.add(new RawBar(LocalDate.of(2026,11,3).minusDays(i),p,p,p,p,new BigDecimal("100"),p.multiply(new BigDecimal("100")),1,null));return result;}
    private List<TradingDay> calendar(){var list=new ArrayList<TradingDay>();for(var d=LocalDate.of(2026,11,3).minusDays(59);!d.isAfter(LocalDate.of(2026,11,10));d=d.plusDays(1))list.add(new TradingDay(d,true));return list;}
    private MarketCandidate candidate(String op,String price,Long revision){return new MarketCandidate(op,"SH600030","SH600030","test",true,"ACTIVE","baostock","RAW",clock.instant(),null,"a".repeat(64),bars(price),calendar().stream().filter(d->!d.date().isAfter(LocalDate.of(2026,11,3))).toList(),revision);}
    @BeforeEach void setup(){f=new MarketDatabaseFixture();market=new MarketVersionRepository(f.jdbc());var pub=new PublicationService(market,new CandidateValidator());old=pub.publish(candidate("old","10",0L)).versionId();head=pub.publish(candidate("head","11",1L)).versionId();uses=new MarketLifecycleService(f.jdbc(),clock,()->true);}
    private MarketLifecycleRecords.Bundle bundle(String purpose){return uses.acquire(new UseIdentity("prediction",purpose,UUID.randomUUID().toString(),"1"),new Selector("RAW_VERSIONS",List.of(old),null,null));}
    private <T>T guarded(MarketLifecycleRecords.Bundle b,java.util.function.Supplier<T> work){try(var scope=new MarketEvidenceGate(uses,true).open(b.bundleId(),b.identity(),Set.of(b.identity().purpose()))){return work.get();}}
    private void release(MarketLifecycleRecords.Bundle b){uses.releaseVerified(b.bundleId(),new MarketLifecycleRecords.TerminalProof(b.identity(),"SUCCEEDED",clock.instant(),"1",b.identity().ownerOperationId(),b.versionIds()));}
    private ForecastCandidate input(String op){return new ForecastCandidate(op,"SH600030",old,"baostock","baostock-backward-close-over-raw-v1",clock.instant(),"b".repeat(64),bars("10").stream().map(b->new ForecastCandidate.PairedBar(b,new BigDecimal("20"))).toList(),calendar());}
    @Test void newlyPublishedRawHasLifecycleAndDisabledOldContentCannotBeRepublished() {
        assertEquals(2,f.jdbc().queryForObject("select count(*) from market_version_lifecycle",Integer.class));
        uses.action("SH600030",old,actor,new Command(UUID.randomUUID().toString(),"DISABLE","0","隔离测试"));
        var pub=new PublicationService(market,new CandidateValidator());
        assertEquals("MARKET_VERSION_USE_BLOCKED",assertThrows(BusinessException.class,()->pub.publish(candidate("retry-old","10",2L))).code());
        assertEquals(head,f.jdbc().queryForObject("select version_id from instrument_data_head where instrument_id='SH600030'",String.class));
    }
    @Test void forecastAndAnalysisStayPinnedAndRejectOtherRaw() {
        var b=bundle("FORECAST");var forecast=new ForecastInputService(market,clock,20);
        assertEquals(old,guarded(b,()->forecast.rawWindow("SH600030")).versionId());
        assertEquals(head,forecast.rawWindow("SH600030").versionId());
        var analysis=bundle("STOCK_ANALYSIS");var query=new MarketQueryService(market,new com.aistock.market.instrument.InstrumentQueryService(f.jdbc()),new com.aistock.market.indicator.TechnicalIndicators(),clock,12,20);
        assertThrows(BusinessException.class,()->guarded(analysis,()->query.daily("SH600030","2026-10-01","2026-11-03","RAW",head)));
        assertEquals(old,guarded(analysis,()->query.daily("SH600030","2026-10-01","2026-11-03","RAW",old)).versionId());
    }
    @Test void drainingAllowsExistingUseButReleasedScopeCannotReadOrPublishInput() {
        var b=bundle("FORECAST");var forecast=new ForecastInputService(market,clock,20);
        uses.action("SH600030",old,actor,new Command(UUID.randomUUID().toString(),"DISABLE","0","隔离测试"));
        guarded(b,()->forecast.publish(input("initial")));
        assertEquals(old,guarded(b,()->forecast.window("SH600030",null,60,5)).rawVersionId());
        try(var scope=new MarketEvidenceGate(uses,true).open(b.bundleId(),b.identity(),Set.of("FORECAST"))) {
            release(b);
            assertThrows(BusinessException.class,()->forecast.rawWindow("SH600030"));
            assertThrows(BusinessException.class,()->forecast.publish(input("late")));
            assertEquals(0,f.jdbc().queryForObject("select count(*) from forecast_input_operation where operation_id='late'",Integer.class));
        }
        assertEquals("DISABLED",uses.snapshot("SH600030",old).useState());
    }
    @Test void wrongOwnerOrGenerationCannotOpenUsageScope() {
        var b=bundle("FORECAST");var gate=new MarketEvidenceGate(uses,true);
        assertThrows(BusinessException.class,()->gate.open(b.bundleId(),new UseIdentity("prediction","FORECAST",b.identity().ownerOperationId(),"2"),Set.of("FORECAST")));
        assertThrows(BusinessException.class,()->gate.open(b.bundleId(),new UseIdentity("notification","ALERT_EVALUATION",b.identity().ownerOperationId(),"1"),Set.of("ALERT_EVALUATION")));
    }
    @Test void comparisonRawValuationAndRealizationUseBoundHistoricalRaw() {
        var producer=uses.acquire(new UseIdentity("market","COMPARISON_EVIDENCE_PUBLISH",UUID.randomUUID().toString(),"1"),new Selector("RAW_VERSIONS",List.of(old),null,null));
        var comparison=new com.aistock.market.comparison.ComparisonEvidenceService(market,clock);
        assertEquals(UUID.fromString(old),guarded(producer,()->comparison.rawWindow("SH600030",LocalDate.of(2026,11,1),LocalDate.of(2026,11,3))).rawVersionId());
        var verify=bundle("PREDICTION_VERIFICATION");var json=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var realization=new com.aistock.market.realization.RealizationRepository(market,json,clock);
        assertEquals(old,guarded(verify,()->realization.head("SH600030")).versionId());
        f.jdbc().update("update instrument_data_head set version_id=? where instrument_id='SH600030'",old);
        var portfolio=uses.acquire(new UseIdentity("prediction","PORTFOLIO_VALUATION",UUID.randomUUID().toString(),"1"),new Selector("CURRENT_HEADS",null,null,List.of("SH600030")));
        f.jdbc().update("update instrument_data_head set version_id=? where instrument_id='SH600030'",head);
        var calendars=org.mockito.Mockito.mock(com.aistock.market.calendar.CalendarService.class);
        var valuations=new com.aistock.market.valuation.ValuationQuoteService(new com.aistock.market.valuation.ValuationQuoteRepository(f.jdbc()),calendars,clock);
        var result=guarded(portfolio,()->valuations.batch(new com.aistock.market.valuation.ValuationQuoteRecords.QuoteRequest(LocalDate.of(2026,11,3),List.of("SH600030"))));
        assertEquals(UUID.fromString(old),result.items().get(0).versionId());
        assertThrows(BusinessException.class,()->guarded(portfolio,()->valuations.batch(new com.aistock.market.valuation.ValuationQuoteRecords.QuoteRequest(LocalDate.of(2026,11,3),List.of("SH600276")))));
    }
    @Test void alertCollectionDoesNotHoldDbLocksAndLateCompletionCannotPublish() {
        var id=new UseIdentity("notification","ALERT_EVALUATION",UUID.randomUUID().toString(),"1");var b=uses.acquire(id,new Selector("RAW_VERSIONS",List.of(old),null,null));
        var collector=org.mockito.Mockito.mock(com.aistock.market.alert.AlertEvidenceCollector.class);
        org.mockito.Mockito.when(collector.collect(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any())).thenAnswer(inv->{
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());release(b);return null;
        });
        var alerts=new com.aistock.market.alert.AlertEvidenceService(market,org.mockito.Mockito.mock(com.aistock.market.calendar.CalendarService.class),collector,clock,20);
        var request=new com.aistock.market.alert.AlertEvidenceRecords.Request(UUID.randomUUID(),"SH600030",UUID.fromString(old),LocalDate.of(2026,11,3),clock.instant().plusSeconds(90));
        var receipt=guarded(b,()->alerts.prepare(request));assertEquals("FAILED",receipt.state());
        assertEquals(0,f.jdbc().queryForObject("select count(*) from alert_evidence_version",Integer.class));
    }
    @Test void localReadAndDisableSerializeWithoutLockingDuringExternalWork()throws Exception {
        var b=bundle("FORECAST");var ready=new java.util.concurrent.CountDownLatch(1);var proceed=new java.util.concurrent.CountDownLatch(1);var entered=new java.util.concurrent.CountDownLatch(1);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var read=pool.submit(()->guarded(b,()->MarketEvidenceGate.local(()->{ready.countDown();try{assertTrue(proceed.await(5,java.util.concurrent.TimeUnit.SECONDS));}catch(InterruptedException e){throw new AssertionError(e);}return true;})));
            assertTrue(ready.await(5,java.util.concurrent.TimeUnit.SECONDS));
            var stop=pool.submit(()->{entered.countDown();return uses.action("SH600030",old,actor,new Command(UUID.randomUUID().toString(),"DISABLE","0","隔离竞争测试"));});assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.TimeoutException.class,()->stop.get(200,java.util.concurrent.TimeUnit.MILLISECONDS));proceed.countDown();assertTrue(read.get(10,java.util.concurrent.TimeUnit.SECONDS));assertEquals("WAITING",stop.get(10,java.util.concurrent.TimeUnit.SECONDS).state());
        } finally {proceed.countDown();pool.shutdownNow();}
    }
    @Test void comparisonMustReadSelectedEvidenceNotAnotherIdWithSameRaw()throws Exception {
        var pub=new PublicationService(market,new CandidateValidator());var c=candidate("other-stock","10",0L);
        var other=pub.publish(new MarketCandidate(c.operationId(),"SH600276","SH600276","test",true,"ACTIVE",c.provider(),c.priceBasis(),c.fetchedAt(),null,c.sourcePayloadHash(),c.bars(),c.calendar(),0L)).versionId();
        var selected=UUID.randomUUID();var unselected=UUID.randomUUID();var selectedOther=UUID.randomUUID();var json=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        for(var pair:List.of(List.of(selected.toString(),"SH600030",old,"c"),List.of(unselected.toString(),"SH600030",old,"d"),List.of(selectedOther.toString(),"SH600276",other,"e"))) {
            var e=new com.aistock.market.comparison.ComparisonEvidenceRecords.Evidence(UUID.fromString(pair.get(0)),pair.get(1),UUID.fromString(pair.get(2)),"a".repeat(64),"baostock","BACKWARD",LocalDate.of(2026,10,1),LocalDate.of(2026,11,3),"b".repeat(64),pair.get(3).repeat(64),"f".repeat(64),clock.instant(),List.of(),List.of());
            f.jdbc().update("insert into comparison_evidence_version(evidence_id,instrument_id,raw_version_id,content_hash,evidence_json,created_at) values(?,?,?,?,?,now(3))",pair.get(0),pair.get(1),pair.get(2),pair.get(3).repeat(64),json.writeValueAsString(e));
        }
        var id=new UseIdentity("prediction","COMPARISON",UUID.randomUUID().toString(),"1");var b=uses.acquire(id,new Selector("COMPARISON_EVIDENCE",null,List.of(new EvidenceBinding("SH600030",selected.toString()),new EvidenceBinding("SH600276",selectedOther.toString())),null));
        var comparison=new com.aistock.market.comparison.ComparisonEvidenceService(market,clock);
        assertEquals(selected,guarded(b,()->comparison.version(selected)).evidenceId());
        assertEquals("MARKET_USE_BINDING_MISMATCH",assertThrows(BusinessException.class,()->guarded(b,()->comparison.version(unselected))).code());
    }
    @Test void rawRepublishAndDisableSerializeInBothOrders()throws Exception {
        for(boolean publicationFirst:List.of(true,false)) {
            if(!publicationFirst)setup();
            var pub=new PublicationService(market,new CandidateValidator());var ready=new java.util.concurrent.CountDownLatch(1);var proceed=new java.util.concurrent.CountDownLatch(1);var entered=new java.util.concurrent.CountDownLatch(1);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
            var stopCommand=new Command(UUID.randomUUID().toString(),"DISABLE","0","隔离发布竞争");
            var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(f.jdbc().getDataSource()));tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
            try {
                var first=pool.submit(()->tx.execute(status->{if(publicationFirst)pub.publish(candidate("reattach","10",2L));else uses.action("SH600030",old,actor,stopCommand);ready.countDown();try{assertTrue(proceed.await(5,java.util.concurrent.TimeUnit.SECONDS));}catch(InterruptedException e){throw new AssertionError(e);}return true;}));
                assertTrue(ready.await(5,java.util.concurrent.TimeUnit.SECONDS));
                var second=pool.submit(()->{entered.countDown();if(publicationFirst)return uses.action("SH600030",old,actor,stopCommand).errorCode();return assertThrows(BusinessException.class,()->pub.publish(candidate("late-reattach","10",2L))).code();});assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
                assertThrows(java.util.concurrent.TimeoutException.class,()->second.get(200,java.util.concurrent.TimeUnit.MILLISECONDS));proceed.countDown();assertTrue(first.get(10,java.util.concurrent.TimeUnit.SECONDS));
                assertEquals(publicationFirst?"MARKET_VERSION_CURRENT_HEAD":"MARKET_VERSION_USE_BLOCKED",second.get(10,java.util.concurrent.TimeUnit.SECONDS));
                assertEquals(publicationFirst?old:head,f.jdbc().queryForObject("select version_id from instrument_data_head where instrument_id='SH600030'",String.class));
            }finally{proceed.countDown();pool.shutdownNow();}
        }
    }
    @Test void releasedAlertScopeCannotReadRawOrCreatePreparationReceipt() {
        var id=new UseIdentity("notification","ALERT_EVALUATION",UUID.randomUUID().toString(),"1");var b=uses.acquire(id,new Selector("RAW_VERSIONS",List.of(old),null,null));
        var observed=org.mockito.Mockito.spy(market);var collector=org.mockito.Mockito.mock(com.aistock.market.alert.AlertEvidenceCollector.class);
        var alerts=new com.aistock.market.alert.AlertEvidenceService(observed,org.mockito.Mockito.mock(com.aistock.market.calendar.CalendarService.class),collector,clock,20);org.mockito.Mockito.clearInvocations(observed);
        var request=new com.aistock.market.alert.AlertEvidenceRecords.Request(UUID.randomUUID(),"SH600030",UUID.fromString(old),LocalDate.of(2026,11,3),clock.instant().plusSeconds(90));
        try(var scope=new MarketEvidenceGate(uses,true).open(b.bundleId(),id,Set.of("ALERT_EVALUATION"))) {
            release(b);assertEquals("MARKET_USE_ALREADY_RELEASED",assertThrows(BusinessException.class,()->alerts.prepare(request)).code());
        }
        org.mockito.Mockito.verify(observed,org.mockito.Mockito.never()).bars(org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.verifyNoInteractions(collector);assertEquals(0,f.jdbc().queryForObject("select count(*) from alert_evidence_operation",Integer.class));
    }
}
