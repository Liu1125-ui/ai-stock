package com.aistock.market.realization;
import com.aistock.market.support.MarketDatabaseFixture;
import com.aistock.market.version.MarketVersionRepository;
import com.aistock.market.ingestion.*;
import com.aistock.market.forecast.ForecastCandidate;
import com.aistock.market.quotation.*;
import com.aistock.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;import java.math.BigDecimal;import java.util.*;
import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;
import static com.aistock.market.realization.RealizationRecords.*;
@Tag("mysql") class RealizationMysqlTest {
 static final Clock NOW=Clock.fixed(Instant.parse("2026-09-23T14:00:00Z"),ZoneOffset.UTC);
 static final LocalDate ORIGIN=LocalDate.parse("2026-09-21");
 static final List<LocalDate> TARGETS=List.of("2026-09-22","2026-09-23","2026-09-24","2026-09-25","2026-09-28").stream().map(LocalDate::parse).toList();
 static final ObjectMapper MAPPER=new ObjectMapper().findAndRegisterModules().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
 final MarketDatabaseFixture f=new MarketDatabaseFixture();final MarketVersionRepository market=new MarketVersionRepository(f.jdbc());
 List<RawBar> bars(){return List.of(ORIGIN,TARGETS.get(0),TARGETS.get(1)).stream().map(d->new RawBar(d,new BigDecimal("10"),new BigDecimal("10"),new BigDecimal("10"),new BigDecimal("10"),new BigDecimal("100"),new BigDecimal("1000"),1,null)).toList();}
 String raw(){return new PublicationService(market,new CandidateValidator()).publish(new MarketCandidate(UUID.randomUUID().toString(),"SH600030","SH600030","中信证券",true,"ACTIVE","baostock","RAW",NOW.instant(),null,"a".repeat(64),bars(),List.of(),0L)).versionId();}
 Request request(){return new Request(UUID.randomUUID(),"SH600030",ORIGIN,TARGETS,TARGETS.get(1),NOW.instant().plusSeconds(90));}
 ForecastCandidate candidate(Request r,Head h){var days=new ArrayList<TradingDay>();for(var d=ORIGIN;!d.isAfter(r.dueThrough().plusDays(5));d=d.plusDays(1))days.add(new TradingDay(d,d.getDayOfWeek().getValue()<6));return new ForecastCandidate(r.operationId().toString(),r.instrumentId(),h.versionId(),"baostock",DEFINITION,NOW.instant(),"b".repeat(64),bars().stream().map(b->new ForecastCandidate.PairedBar(b,new BigDecimal("20"))).toList(),days);}
 RealizationService service(RealizationCollector collector){return new RealizationService(new RealizationRepository(market,MAPPER,NOW),collector,new RealizationValidator(NOW,20),MAPPER,NOW);}
 @Test void concurrentDifferentOperationsDeduplicateSameContent()throws Exception{raw();var entered=new java.util.concurrent.CountDownLatch(2);var release=new java.util.concurrent.CountDownLatch(1);var s=service((r,h)->{entered.countDown();try{if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("release timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return new Source("AVAILABLE",candidate(r,h),null);});var executor=java.util.concurrent.Executors.newFixedThreadPool(2);try{var first=executor.submit(()->s.prepare(request()));var second=executor.submit(()->s.prepare(request()));assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));release.countDown();var a=first.get(5,java.util.concurrent.TimeUnit.SECONDS);var b=second.get(5,java.util.concurrent.TimeUnit.SECONDS);assertEquals("AVAILABLE",a.state());assertEquals("AVAILABLE",b.state());assertEquals(a.versionId(),b.versionId());assertEquals(1,f.jdbc().queryForObject("select count(*) from realization_version",Integer.class));}finally{release.countDown();executor.shutdownNow();}}
 @Test void fixedEvidenceRetainsOriginalCalendarAfterSourceChanges(){raw();var s=service((r,h)->new Source("AVAILABLE",candidate(r,h),null));var r=request();var a=s.prepare(r);var json=MAPPER.valueToTree(s.version(a.versionId()));assertTrue(json.path("calendar").isArray());assertEquals(candidate(r,new RealizationRepository(market,MAPPER,NOW).head(r.instrumentId())).calendar().size(),json.path("calendar").size());var revised=service((req,h)->{var c=candidate(req,h);var days=new ArrayList<>(c.calendar());days.set(days.size()-1,new TradingDay(days.get(days.size()-1).date(),false));return new Source("AVAILABLE",new ForecastCandidate(c.operationId(),c.instrumentId(),c.rawVersionId(),c.provider(),c.definition(),c.fetchedAt(),c.sourcePayloadHash(),c.bars(),days),null);});var b=revised.prepare(request());assertNotEquals(a.versionId(),b.versionId());assertEquals(json,MAPPER.valueToTree(s.version(a.versionId())));}
 @Test void operationReadExpiresAbandonedLease(){var r=request();var repo=new RealizationRepository(market,MAPPER,NOW);assertNull(repo.begin(r,"a".repeat(64)));var later=new RealizationRepository(market,MAPPER,Clock.fixed(NOW.instant().plusSeconds(121),ZoneOffset.UTC));assertEquals("ATTEMPT_EXPIRED",later.operation(r.operationId()).errorCode());assertEquals("FAILED",later.operation(r.operationId()).state());assertEquals(0,f.jdbc().queryForObject("select count(*) from realization_version",Integer.class));}
 @Test void publishesImmutableEvidenceAndReplaysWithoutCollectingAgain(){var raw=raw();var calls=new java.util.concurrent.atomic.AtomicInteger();var s=service((r,h)->{calls.incrementAndGet();return new Source("AVAILABLE",candidate(r,h),null);});var r=request();var result=s.prepare(r);assertEquals("AVAILABLE",result.state());assertEquals(result,s.prepare(r));assertEquals(1,calls.get());var e=s.version(result.versionId());assertEquals(raw,e.rawVersionId());assertEquals("20",e.originBackwardClose());assertEquals(2,e.observations().size());assertEquals(raw,f.jdbc().queryForObject("select version_id from instrument_data_head",String.class));assertEquals(result,s.operation(r.operationId()));}
 @Test void sameEvidenceNewOperationReusesVersionButChangedParametersConflict(){raw();var s=service((r,h)->new Source("AVAILABLE",candidate(r,h),null));var r=request();var a=s.prepare(r);assertEquals(a.versionId(),s.prepare(request()).versionId());var changed=new Request(r.operationId(),r.instrumentId(),r.originDate(),r.targetDates(),TARGETS.get(0),r.leaseUntil());assertEquals("REALIZATION_OPERATION_CONFLICT",assertThrows(BusinessException.class,()->s.prepare(changed)).code());assertEquals(1,f.jdbc().queryForObject("select count(*) from realization_version",Integer.class));}
 @Test void sourceFailureAndExpiredAttemptDoNotCreateEvidence(){var raw=raw();var s=service((r,h)->new Source("SOURCE_TIMEOUT",null,"SOURCE_TIMEOUT"));assertEquals("FAILED",s.prepare(request()).state());var r=request();var expired=new Request(r.operationId(),r.instrumentId(),r.originDate(),r.targetDates(),r.dueThrough(),NOW.instant().minusSeconds(1));assertEquals("ATTEMPT_EXPIRED",s.prepare(expired).errorCode());assertEquals(0,f.jdbc().queryForObject("select count(*) from realization_version",Integer.class));assertEquals(raw,f.jdbc().queryForObject("select version_id from instrument_data_head",String.class));}
 @Test void noDueTargetSkipsSourceAndNoVersion(){var s=service((r,h)->{throw new AssertionError("must not collect");});var r=new Request(UUID.randomUUID(),"SH600030",ORIGIN,TARGETS,ORIGIN,NOW.instant().plusSeconds(90));assertEquals("SKIPPED_NOT_DUE",s.prepare(r).state());assertEquals(0,f.jdbc().queryForObject("select count(*) from realization_version",Integer.class));}
 @Test void invalidPairedRawCalendarFactorAndIdentityAreRejected(){raw();for(int kind=0;kind<4;kind++){final int k=kind;var s=service((r,h)->{var c=candidate(r,h);var pairs=new ArrayList<>(c.bars());var days=new ArrayList<>(c.calendar());if(k==0)pairs.set(1,new ForecastCandidate.PairedBar(new RawBar(TARGETS.get(0),BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,new BigDecimal("100"),new BigDecimal("1000"),1,null),new BigDecimal("20")));if(k==1)days.remove(1);if(k==2)pairs.set(0,new ForecastCandidate.PairedBar(pairs.get(0).raw(),BigDecimal.ZERO));return new Source("AVAILABLE",new ForecastCandidate(c.operationId(),k==3?"SZ000002":c.instrumentId(),c.rawVersionId(),c.provider(),c.definition(),c.fetchedAt(),c.sourcePayloadHash(),pairs,days),null);});assertEquals("FAILED",s.prepare(request()).state());}assertEquals(0,f.jdbc().queryForObject("select count(*) from realization_version",Integer.class));}
 @Test void historicalMissingAndSuspensionAreSeparateFromFuture(){var h=new Head(UUID.randomUUID().toString(),"a".repeat(64),bars());var r=request();var c=candidate(r,h);var pairs=List.of(c.bars().get(0));var missing=new ForecastCandidate(c.operationId(),c.instrumentId(),c.rawVersionId(),c.provider(),c.definition(),c.fetchedAt(),c.sourcePayloadHash(),pairs,c.calendar());assertThrows(BusinessException.class,()->new RealizationValidator(NOW,20).evidence(r,h,missing));}
 @Test void expiredDuringRawReadDoesNotStartCollector(){
  raw();var r=request();
  var instant=new java.util.concurrent.atomic.AtomicReference<>(NOW.instant());
  var clock=new Clock(){public ZoneId getZone(){return ZoneId.of("Asia/Shanghai");}public Clock withZone(ZoneId z){return this;}public Instant instant(){return instant.get();}};
  var delayed=org.mockito.Mockito.spy(market);
  org.mockito.Mockito.doAnswer(call->{var bars=call.callRealMethod();instant.set(r.leaseUntil().plusSeconds(1));return bars;}).when(delayed).bars(org.mockito.ArgumentMatchers.anyString());
  var collector=org.mockito.Mockito.mock(RealizationCollector.class);
  var s=new RealizationService(new RealizationRepository(delayed,MAPPER,clock),collector,new RealizationValidator(clock,20),MAPPER,clock);
  var done=s.prepare(r);
  org.mockito.Mockito.verifyNoInteractions(collector);
  assertEquals("FAILED",done.state());
  assertEquals("ATTEMPT_EXPIRED",done.errorCode());
  assertEquals(0,f.jdbc().queryForObject("select count(*) from realization_version",Integer.class));
 }

 @Test void stalePublishedRawWaitsWithoutCallingCollector(){
  var bars=List.of(bars().get(0));
  new PublicationService(market,new CandidateValidator()).publish(new MarketCandidate(UUID.randomUUID().toString(),"SH600030","SH600030","中信证券",true,"ACTIVE","baostock","RAW",NOW.instant(),null,"a".repeat(64),bars,List.of(),0L));
  var collector=org.mockito.Mockito.mock(RealizationCollector.class);
  var result=service(collector).prepare(request());
  assertEquals("REALIZATION_RAW_UNAVAILABLE",result.errorCode());
  org.mockito.Mockito.verifyNoInteractions(collector);
 }
}
