package com.aistock.market.refresh;
import com.aistock.common.BusinessException;
import com.aistock.market.ingestion.*;
import com.aistock.market.quotation.*;
import com.aistock.market.support.MarketDatabaseFixture;
import com.aistock.market.version.MarketVersionRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("mysql")
class RefreshMysqlTest {
    private final LocalDate start=LocalDate.of(2026,9,24);
    private MarketCandidate candidate(String op, Long revision, boolean full) {
        var price=new BigDecimal("10");
        var bars=List.of(new RawBar(start,price,price,price,price,BigDecimal.ONE,price,1,null),
            new RawBar(start.plusDays(1),price,price,price,price,BigDecimal.ONE,price,1,null));
        return new MarketCandidate(op,"SH600030","SH600030","SH600030",false,"UNKNOWN","baostock","RAW",Instant.parse("2026-09-26T08:00:00Z"),"original","a".repeat(64),bars,
            full?List.of(new TradingDay(start,true),new TradingDay(start.plusDays(1),true)):List.of(),revision);
    }
    private PublicationService publisher(MarketDatabaseFixture f) { return new PublicationService(new MarketVersionRepository(f.jdbc()),new CandidateValidator()); }
    private String initial(MarketDatabaseFixture f) {return publisher(f).publish(candidate("initial",0L,false)).versionId();}
    private RefreshRequest request(String op) {return new RefreshRequest(op,"SH600030",start.plusDays(1),Instant.now().plusSeconds(90));}
    private RefreshService service(MarketDatabaseFixture f, MarketCollector collector) {
        return new RefreshService(new RefreshRepository(f.jdbc()),publisher(f),collector,Clock.systemUTC());
    }
    @Test void listedStockCanPublishItsFirstRawVersionThroughRefresh() {
        var f=new MarketDatabaseFixture();
        f.jdbc().update("insert into instrument(instrument_id,ticker,display_name,demo_only,listing_status,name_verified) values('SH600030','SH600030','中信证券',false,'ACTIVE',true)");
        var s=service(f,(op,id,a,b)->{
            assertEquals(b.minusDays(180),a);
            var calendar=a.datesUntil(b.plusDays(1)).map(day->new TradingDay(day,!day.isBefore(start))).toList();
            var sample=candidate(op,null,true);
            var loaded=new MarketCandidate(op,id,id,id,false,"UNKNOWN","baostock","RAW",sample.fetchedAt(),
                sample.sourceVersionId(),sample.sourcePayloadHash(),sample.bars(),calendar,null);
            return new MarketCollector.Collected(op,"AVAILABLE",loaded,null);
        });
        var result=s.refresh(request("first-listed"));
        assertEquals("AVAILABLE",result.state());
        assertEquals("中信证券",f.jdbc().queryForObject("select display_name from instrument where instrument_id='SH600030'",String.class));
        assertEquals(1,f.jdbc().queryForObject("select revision from instrument_data_head",Integer.class));
    }
    @Test void newUnknownStockCannotTriggerSourceReads() {
        var f=new MarketDatabaseFixture();
        var s=service(f,(op,id,a,b)->{throw new AssertionError("must not collect unknown stock");});
        assertEquals("REAL_MARKET_VERSION_REQUIRED",s.refresh(request("first-unknown")).errorCode());
    }
    @Test void completeCalendarPublishesAndFinishedReceiptReplaysWithoutCollecting() {
        var f=new MarketDatabaseFixture();var old=initial(f);var calls=new AtomicInteger();
        var s=service(f,(op,id,a,b)->{calls.incrementAndGet();return new MarketCollector.Collected(op,"AVAILABLE",candidate(op,null,true),null);});
        var req=request("refresh-good");var result=s.refresh(req);
        assertEquals("AVAILABLE",result.state());assertNotEquals(old,result.versionId());
        assertEquals(result,s.refresh(req));assertEquals(1,calls.get());
        var conflict=new RefreshRequest(req.operationId(),req.instrumentId(),start,req.leaseUntil());
        assertEquals("REFRESH_OPERATION_CONFLICT",assertThrows(BusinessException.class,()->s.refresh(conflict)).code());
    }
    @Test void optionalValuationFollowsSuccessfulRawPublicationAndIsolatedFailureDoesNotUndoIt() {
        var f = new MarketDatabaseFixture();
        initial(f);
        var store = new StockValuationStore(f.jdbc(), new com.aistock.market.instrument.InstrumentQueryService(f.jdbc()));
        var source = (MarketCollector)(op,id,a,b) -> new MarketCollector.Collected(op,"AVAILABLE",
                candidate(op,null,true),null,new StockValuationStore.Sample(b,"-8.125","1.2"));
        var refresh = new RefreshService(new RefreshRepository(f.jdbc()), publisher(f), source, Clock.systemUTC(), store);
        var saved = refresh.refresh(request("with-valuation"));
        assertEquals("AVAILABLE", saved.state());
        assertEquals("-8.125", store.read("SH600030",saved.versionId()).peTtm());
        var unavailable = org.mockito.Mockito.mock(StockValuationStore.class);
        org.mockito.Mockito.when(unavailable.target(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenThrow(new IllegalStateException("database unavailable"));
        var stillWorks = new RefreshService(new RefreshRepository(f.jdbc()), publisher(f), source, Clock.systemUTC(), unavailable);
        assertEquals("AVAILABLE", stillWorks.refresh(request("without-valuation")).state());
    }

    @Test void incompleteCalendarIsRejectedAndOldHeadRetained() {
        var f=new MarketDatabaseFixture();var old=initial(f);
        var result=service(f,(op,id,a,b)->new MarketCollector.Collected(op,"AVAILABLE",candidate(op,null,false),null)).refresh(request("bad-calendar"));
        assertEquals("FAILED",result.state());assertEquals("MARKET_DATA_INCOMPLETE",result.errorCode());
        assertEquals(old,f.jdbc().queryForObject("select version_id from instrument_data_head",String.class));
    }
    @Test void sourceTimeoutIsNotEmptyAndRecordedWithoutLosingOldHead() {
        var f=new MarketDatabaseFixture();var old=initial(f);
        var result=service(f,(op,id,a,b)->new MarketCollector.Collected(op,"SOURCE_TIMEOUT",null,"SOURCE_TIMEOUT")).refresh(request("timeout"));
        assertEquals("FAILED",result.state());assertEquals("SOURCE_TIMEOUT",result.errorCode());
        assertEquals(old,f.jdbc().queryForObject("select version_id from instrument_data_head",String.class));
        assertEquals("SOURCE_TIMEOUT",f.jdbc().queryForObject("select last_error_code from instrument_data_head",String.class));
    }
    @Test void expiredLeaseCannotPublishEvenAfterValidCandidate() {
        var f=new MarketDatabaseFixture();var old=initial(f);
        var clock=new MutableClock();var req=new RefreshRequest("expired","SH600030",start.plusDays(1),clock.instant().plusSeconds(90));
        var s=new RefreshService(new RefreshRepository(f.jdbc()),publisher(f),(op,id,a,b)->{
            clock.now=clock.now.plusSeconds(91);return new MarketCollector.Collected(op,"AVAILABLE",candidate(op,null,true),null);
        },clock);
        var result=s.refresh(req);assertEquals("ATTEMPT_EXPIRED",result.errorCode());
        assertEquals(old,f.jdbc().queryForObject("select version_id from instrument_data_head",String.class));
        assertEquals(1,f.jdbc().queryForObject("select count(*) from market_data_version",Integer.class));
    }
    @Test void publicationFenceCheckedInsideTransaction() {
        var f=new MarketDatabaseFixture();var old=initial(f);
        var e=assertThrows(BusinessException.class,()->publisher(f).publish(candidate("direct-expired",1L,true),Instant.now().minusSeconds(1),Clock.systemUTC()));
        assertEquals("ATTEMPT_EXPIRED",e.code());assertEquals(old,f.jdbc().queryForObject("select version_id from instrument_data_head",String.class));
    }
    @Test void runningReceiptConflictAndFutureTargetRejected() {
        var f=new MarketDatabaseFixture();initial(f);var req=request("running");
        var repo=new RefreshRepository(f.jdbc());repo.begin(req,Clock.systemUTC());
        var s=service(f,(op,id,a,b)->{throw new AssertionError("must not collect");});
        assertEquals("REFRESH_RUNNING",assertThrows(BusinessException.class,()->s.refresh(req)).code());
        var future=new RefreshRequest("future","SH600030",LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1),req.leaseUntil());
        assertThrows(BusinessException.class,()->s.refresh(future));
    }
    @Test void emptyFullHistoryIsIncompleteNotSuccessfulEmpty() {
        var f=new MarketDatabaseFixture();var old=initial(f);
        var result=service(f,(op,id,a,b)->new MarketCollector.Collected(op,"LEGITIMATE_EMPTY",null,null)).refresh(request("empty-history"));
        assertEquals("FAILED",result.state());assertEquals("MARKET_DATA_INCOMPLETE",result.errorCode());
        assertEquals(old,f.jdbc().queryForObject("select version_id from instrument_data_head",String.class));
    }
    @Test void publicationLockWaitCannotCrossDeadlineOrLeaveFalseReceipt()throws Exception {
        var f=new MarketDatabaseFixture();var old=initial(f);var clock=new MutableClock();
        var req=new RefreshRequest("lock-expiry","SH600030",start.plusDays(1),clock.instant().plusSeconds(90));
        var s=new RefreshService(new RefreshRepository(f.jdbc()),publisher(f),(op,id,a,b)->new MarketCollector.Collected(op,"AVAILABLE",candidate(op,null,true),null),clock);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try(var connection=f.jdbc().getDataSource().getConnection()){
            connection.setAutoCommit(false);
            try(var statement=connection.prepareStatement("SELECT * FROM source_code_map WHERE provider='baostock' AND source_code='sh.600030' FOR UPDATE")){statement.executeQuery().close();}
            var original=pool.submit(()->s.refresh(req));
            boolean blocked=false;
            for(int i=0;i<100;i++){if(f.jdbc().queryForObject("SELECT count(*) FROM performance_schema.data_lock_waits",Integer.class)>0){blocked=true;break;}Thread.sleep(20);}
            assertTrue(blocked,"publication must reach actual database lock wait");
            clock.now=clock.now.plusSeconds(91);
            var duplicate=pool.submit(()->s.refresh(req));
            connection.commit();
            assertEquals("ATTEMPT_EXPIRED",original.get(10,java.util.concurrent.TimeUnit.SECONDS).errorCode());
            assertEquals("ATTEMPT_EXPIRED",duplicate.get(10,java.util.concurrent.TimeUnit.SECONDS).errorCode());
            assertEquals(old,f.jdbc().queryForObject("select version_id from instrument_data_head",String.class));
            assertEquals("FAILED",f.jdbc().queryForObject("select state from market_refresh_receipt where operation_id=?",String.class,req.operationId()));
        }finally{pool.shutdownNow();}
    }
    @Test void committedReceiptCannotBeOverwrittenByLateFailure() {
        var f=new MarketDatabaseFixture();initial(f);var req=request("atomic-receipt");var repo=new RefreshRepository(f.jdbc());
        repo.begin(req,Clock.systemUTC());
        var success=publisher(f).publish(candidate(req.operationId(),1L,true),req.leaseUntil(),Clock.systemUTC());
        assertEquals("AVAILABLE",f.jdbc().queryForObject("select state from market_refresh_receipt where operation_id=?",String.class,req.operationId()));
        repo.finish(new RefreshResult(req.operationId(),"FAILED",null,"ATTEMPT_EXPIRED"),Instant.now());
        assertEquals("AVAILABLE",f.jdbc().queryForObject("select state from market_refresh_receipt where operation_id=?",String.class,req.operationId()));
    }
    @Test void committedNoChangePublicationDoesNotAcquireFalseFailureWarning() {
        var f=new MarketDatabaseFixture();publisher(f).publish(candidate("initial-full",0L,true));
        var p=new PublicationService(new MarketVersionRepository(f.jdbc()),new CandidateValidator()){
            @Override public Publication publish(MarketCandidate c,Instant deadline,Clock clock){
                super.publish(c,deadline,clock);
                throw new BusinessException("SOURCE_UNAVAILABLE","lost commit reply",org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
            }
        };
        var s=new RefreshService(new RefreshRepository(f.jdbc()),p,(op,id,a,b)->new MarketCollector.Collected(op,"AVAILABLE",candidate(op,null,true),null),Clock.systemUTC());
        assertEquals("AVAILABLE",s.refresh(request("ambiguous-commit")).state());
        assertNull(f.jdbc().queryForObject("select last_error_code from instrument_data_head",String.class));
    }
    private static class MutableClock extends Clock {
        volatile Instant now=Instant.now();
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return now;}
    }
}
