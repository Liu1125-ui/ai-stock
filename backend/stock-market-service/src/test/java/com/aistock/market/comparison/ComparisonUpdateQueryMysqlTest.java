package com.aistock.market.comparison;
import com.aistock.common.BusinessException;
import com.aistock.market.calendar.*;
import com.aistock.market.support.MarketDatabaseFixture;
import com.aistock.market.version.MarketVersionRepository;
import org.junit.jupiter.api.*;
import java.time.*;import java.util.*;import java.sql.Timestamp;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.aistock.market.comparison.ComparisonUpdateSamples.*;

@Tag("mysql")
class ComparisonUpdateQueryMysqlTest {
 private ComparisonUpdateQuery query(MarketDatabaseFixture f){
  return new ComparisonUpdateQuery(new MarketVersionRepository(f.jdbc()),validCalendar(END.minusDays(10),END,NOW),NOW);
 }
 @Test void suspendedTargetIsReadyAndReadHasNoWrites(){
  var f=new MarketDatabaseFixture();var raw=raw(f,"r",START,END,"10",true,0,NOW);
  var before=f.jdbc().queryForObject("select count(*) from market_data_version",Integer.class);
  var item=query(f).options(List.of("SH600030")).items().get(0);
  assertEquals("READY",item.state());assertEquals(raw,item.rawVersionId());assertEquals(START,item.rangeStart());
  assertEquals(before,f.jdbc().queryForObject("select count(*) from market_data_version",Integer.class));
  assertEquals(0,f.jdbc().queryForObject("select count(*) from comparison_evidence_version",Integer.class));
 }
 @Test void rawLaggingAndInsufficientHistoryAreExplicit(){
  var f=new MarketDatabaseFixture();raw(f,"r",START,END.minusDays(1),"10",false,0,NOW);
  assertEquals("RAW_LAGGING",query(f).options(List.of("SH600030")).items().get(0).state());
  raw(f,"next",START.plusDays(1),END,"10",false,1,NOW);
  assertEquals("INSUFFICIENT_HISTORY",query(f).options(List.of("SH600030")).items().get(0).state());
 }
 @Test void unknownStockIsPerItemNotBatchFailure(){
  var f=new MarketDatabaseFixture();raw(f,"r",START,END,"10",false,0,NOW);
  var result=query(f).options(List.of("SH600030","SH600999"));
  assertEquals("READY",result.items().get(0).state());assertEquals("NO_RAW_VERSION",result.items().get(1).state());
 }
 @Test void fixedWindowKeepsRequestedVersionAfterHeadMoves(){
  var f=new MarketDatabaseFixture();var old=raw(f,"r",START,END,"10",false,0,NOW);
  var r=request(f,old,START,END,NOW);
  raw(f,"next",START,END,"11",false,1,NOW);
  var got=query(f).fixedWindow(r);
  assertEquals(old,got.rawVersionId());assertEquals(r.rawContentHash(),got.rawContentHash());assertEquals("10",got.bars().get(0).close());
 }
 @Test void sameProjectionRemainsCurrentWhenRawHeadIsNew(){
  var f=new MarketDatabaseFixture();var id=raw(f,"r",START,END,"10",false,0,NOW);
  var service=new ComparisonEvidenceService(new MarketVersionRepository(f.jdbc()),NOW);
  var evidence=service.publish(candidate(request(f,id,START,END,NOW),"10","20",NOW)).evidenceId();
  raw(f,"next",START.minusDays(2),END,"10.000",false,1,NOW);
  var item=query(f).options(List.of("SH600030")).items().get(0);
  assertEquals("CURRENT",item.state());assertEquals(evidence,item.evidenceId());
 }
 @Test void changedProjectionRequiresNewEvidence(){
  var f=new MarketDatabaseFixture();var id=raw(f,"r",START,END,"10",false,0,NOW);
  new ComparisonEvidenceService(new MarketVersionRepository(f.jdbc()),NOW).publish(candidate(request(f,id,START,END,NOW),"10","20",NOW));
  raw(f,"next",START,END,"11",false,1,NOW);
  assertEquals("READY",query(f).options(List.of("SH600030")).items().get(0).state());
 }
 @Test void missingTargetInEvidenceIsNotCurrentButHistoricalMissingIsPreserved(){
  var f=new MarketDatabaseFixture();var id=raw(f,"r",START,END,"10",false,0,NOW);
  var c=candidate(request(f,id,START,END,NOW),"10","20",NOW);
  var s=new ComparisonEvidenceService(new MarketVersionRepository(f.jdbc()),NOW);
  s.publish(new ComparisonEvidenceRecords.Candidate("missing-target",c.instrumentId(),c.rawVersionId(),c.from(),c.to(),c.provider(),
   c.definition(),c.fetchedAt(),c.sourcePayloadHash(),c.points().subList(0,c.points().size()-1),c.calendar()));
  assertEquals("READY",query(f).options(List.of("SH600030")).items().get(0).state());
  s.publish(new ComparisonEvidenceRecords.Candidate("missing-history",c.instrumentId(),c.rawVersionId(),c.from(),c.to(),c.provider(),
   c.definition(),c.fetchedAt(),c.sourcePayloadHash(),c.points().subList(1,c.points().size()),c.calendar()));
  assertEquals("CURRENT",query(f).options(List.of("SH600030")).items().get(0).state());
 }
 @Test void staleCalendarDoesNotCollectOrPretendReady(){
  var f=new MarketDatabaseFixture();raw(f,"r",START,END,"10",false,0,NOW);
  var collector=mock(CalendarCollector.class);
  var calendar=new CalendarService(f.jdbc(),collector,NOW,7);
  var c=new CalendarRecords.Candidate("baostock",END.minusDays(10),END,NOW.instant().minus(Duration.ofDays(8)),
   "a".repeat(64),List.of());
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  try {
   f.jdbc().update("insert into independent_calendar_version(version_id,provider,range_start,range_end,fetched_at,source_hash,candidate_json) values(?,?,?,?,?,?,?)",
    UUID.randomUUID().toString(),"baostock",c.start(),c.end(),Timestamp.from(c.fetchedAt()),c.sourcePayloadHash(),mapper.writeValueAsString(c));
  }catch(Exception e){throw new AssertionError(e);}
  assertEquals("CALENDAR_UNAVAILABLE",new ComparisonUpdateQuery(new MarketVersionRepository(f.jdbc()),calendar,NOW).options(List.of("SH600030")).items().get(0).state());
  verifyNoInteractions(collector);
 }
 @Test void brokenHashAndFutureCalendarAreRejected(){
  var f=new MarketDatabaseFixture();raw(f,"r",START,END,"10",false,0,NOW);
  var s=mock(CalendarService.class);
  var days=calendar(END.minusDays(10),END).stream().map(d->new CalendarRecords.Day(d.date(),d.open())).toList();
  for(var c:List.of(new CalendarRecords.Candidate("baostock",END.minusDays(10),END,NOW.instant(),"a".repeat(64),days),
    new CalendarRecords.Candidate("baostock",END.minusDays(10),END,NOW.instant().plusSeconds(6),CalendarService.hash(days),days))){
   when(s.covering(any(),any())).thenReturn(Optional.of(new CalendarRecords.Snapshot(UUID.randomUUID(),c)));
   assertEquals("CALENDAR_UNAVAILABLE",new ComparisonUpdateQuery(new MarketVersionRepository(f.jdbc()),s,NOW).options(List.of("SH600030")).items().get(0).state());
  }
 }
 @Test void fixedHashFutureEndAndIncompleteRangeAreRejected(){
  var f=new MarketDatabaseFixture();var id=raw(f,"r",START,END,"10",false,0,NOW);var q=query(f);
  var r=request(f,id,START,END,NOW);
  var wrong=new ComparisonUpdateRecords.Request(r.operationId(),r.instrumentId(),id,"0".repeat(64),START,END,r.definition(),r.leaseUntil());
  assertThrows(BusinessException.class,()->q.fixedWindow(wrong));
  assertThrows(BusinessException.class,()->q.fixedWindow(request(f,id,START.plusDays(1),END,NOW)));
  assertThrows(BusinessException.class,()->q.fixedWindow(request(f,id,START.plusDays(1),END.plusDays(1),NOW)));
 }

 @Test void matchingYearCannotHideAnActiveIncompatibleShortWindow(){
  var f=new MarketDatabaseFixture();var clock=new MutableClock(NOW.instant());var market=new MarketVersionRepository(f.jdbc());
  var service=new ComparisonEvidenceService(market,clock);
  var a=raw(f,"year-A",START,END,"10",false,0,clock);
  service.publish(candidate(request(f,a,START,END,clock),"10","20",clock));
  var b=raw(f,"short-B",START,END,"11",false,1,clock);
  // Same timestamp deliberately checks the deterministic publication tie breaker.
  service.publish(candidate(request(f,b,END.minusMonths(6),END,clock),"11","21",clock));
  assertEquals(a,raw(f,"year-A-reverted",START,END,"10",false,2,clock));
  var query=new ComparisonUpdateQuery(market,validCalendar(END.minusDays(10),END,clock),clock);
  assertEquals("READY",query.options(List.of("SH600030")).items().get(0).state());
  service.publish(candidate(request(f,a,START,END,clock),"10","20",clock));
  assertEquals("CURRENT",query.options(List.of("SH600030")).items().get(0).state());
 }

 @Test void rawReversionAndReusedEvidenceAgreeWithNewComparisonSelection(){
  var f=new MarketDatabaseFixture();var clock=new MutableClock(NOW.instant());var market=new MarketVersionRepository(f.jdbc());
  var service=new ComparisonEvidenceService(market,clock);
  var a=raw(f,"raw-A",START,END,"10",false,0,clock);
  var ea=service.publish(candidate(request(f,a,START,END,clock),"10","20",clock)).evidenceId();
  clock.advance(Duration.ofMillis(10));
  var b=raw(f,"raw-B",START,END,"11",false,1,clock);
  var eb=service.publish(candidate(request(f,b,START,END,clock),"11","21",clock)).evidenceId();
  clock.advance(Duration.ofMillis(10));
  assertEquals(a,raw(f,"raw-A-reverted",START,END,"10",false,2,clock));
  var other="SH600276";
  var otherRaw=new com.aistock.market.ingestion.PublicationService(market,new com.aistock.market.ingestion.CandidateValidator()).publish(
   new com.aistock.market.ingestion.MarketCandidate("other",other,other,other,true,"ACTIVE","baostock","RAW",clock.instant(),null,
    "a".repeat(64),bars(START,END,"10",false),calendar(START,END),0L));
  var oid=UUID.fromString(otherRaw.versionId());
  var or=new ComparisonUpdateRecords.Request(UUID.randomUUID(),other,oid,rawHash(f,oid),START,END,
   ComparisonEvidenceValidator.DEFINITION,clock.instant().plusSeconds(90));
  service.publish(candidate(or,"10","20",clock));
  var query=new ComparisonUpdateQuery(market,validCalendar(END.minusDays(10),END,clock),clock);
  assertEquals("READY",query.options(List.of("SH600030")).items().get(0).state(),"older matching evidence is not the active selection");
  var reused=service.publish(candidate(request(f,a,START,END,clock),"10","20",clock)).evidenceId();
  assertEquals(ea,reused);
  var ready=query.options(List.of("SH600030")).items().get(0);assertEquals("CURRENT",ready.state());assertEquals(ea,ready.evidenceId());
  for(var period:List.of("3M","6M","1Y")){
   var selected=service.options(List.of("SH600030",other),period);assertTrue(selected.available());
   assertEquals(ea,selected.stocks().get(0).evidenceId(),"new comparison must use republished A for "+period);
  }
  assertEquals("21",service.version(eb).days().get(0).backwardClose(),"historical B remains immutable");
  assertEquals(2,f.jdbc().queryForObject("select count(*) from comparison_evidence_version where instrument_id='SH600030'",Integer.class));
 }
}
