package com.aistock.market.forecast;
import com.aistock.common.BusinessException;
import com.aistock.market.ingestion.*;
import com.aistock.market.quotation.*;
import com.aistock.market.support.MarketDatabaseFixture;
import com.aistock.market.version.MarketVersionRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("mysql")
class ForecastInputMysqlTest {
 private static final Clock NOW=Clock.fixed(Instant.parse("2026-10-02T14:00:00Z"),ZoneOffset.UTC);
 private static final String DEFINITION="baostock-backward-close-over-raw-v1";
 private List<RawBar> bars(String price){
  var days=new ArrayList<LocalDate>();var d=LocalDate.of(2026,9,30);
  while(days.size()<60){if(d.getDayOfWeek().getValue()<6)days.add(d);d=d.minusDays(1);}Collections.reverse(days);
  return days.stream().map(day->new RawBar(day,new BigDecimal(price),new BigDecimal(price),new BigDecimal(price),new BigDecimal(price),new BigDecimal("100"),new BigDecimal(price).multiply(new BigDecimal("100")),1,null)).toList();
 }
 private List<TradingDay> calendar(List<RawBar> bars){
  var ans=new ArrayList<TradingDay>();var start=bars.get(0).date();var end=LocalDate.of(2026,10,16);
  for(var d=start;!d.isAfter(end);d=d.plusDays(1))ans.add(new TradingDay(d,d.getDayOfWeek().getValue()<6 && !(d.getMonthValue()==10 && d.getDayOfMonth()<=7)));
  return ans;
 }
 private String raw(MarketDatabaseFixture f,String op,String price,Long revision){
  return new PublicationService(new MarketVersionRepository(f.jdbc()),new CandidateValidator()).publish(new MarketCandidate(op,"SH600030","SH600030","中信证券",true,"ACTIVE","baostock","RAW",NOW.instant(),null,"a".repeat(64),bars(price),List.of(),revision)).versionId();
 }
 private ForecastCandidate candidate(String op,String version){
  var bars=bars("10");return new ForecastCandidate(op,"SH600030",version,"baostock",DEFINITION,NOW.instant(),"b".repeat(64),bars.stream().map(b->new ForecastCandidate.PairedBar(b,new BigDecimal("20"))).toList(),calendar(bars));
 }
 private ForecastInputService service(MarketDatabaseFixture f){return new ForecastInputService(new MarketVersionRepository(f.jdbc()),NOW,20);}
 private void code(String expected,Runnable action){assertEquals(expected,assertThrows(BusinessException.class,action::run).code());}
 @Test void watchlistReadinessDistinguishesMissingRawAndPreparedInputWithoutWriting(){
  var f=new MarketDatabaseFixture();var s=service(f);
  assertEquals("NO_DATA",s.readiness("SH600030"));
  var v=raw(f,"readiness-raw","10",0L);
  assertEquals("CHECK_ON_SUBMIT",s.readiness("SH600030"));
  assertEquals(0,f.jdbc().queryForObject("select count(*) from forecast_input_version",Integer.class));
  s.publish(candidate("readiness-input",v));
  assertEquals("READY",s.readiness("SH600030"));
  assertEquals(1,f.jdbc().queryForObject("select count(*) from forecast_input_version",Integer.class));
 }
 @Test void sourcePreparationReadsCurrentRawHeadWithExactlySixtyFrozenRows(){
  var f=new MarketDatabaseFixture();var first=raw(f,"raw-1","10",0L);var s=service(f);
  var w=s.rawWindow("SH600030");assertEquals(first,w.versionId());assertEquals(60,w.bars().size());
  assertEquals("10",w.bars().get(59).close());assertNull(w.bars().get(0).sourceAdjustmentRatio());
  code("RESOURCE_NOT_FOUND",()->s.rawWindow("SZ000002"));
  var second=raw(f,"raw-2","11",1L);assertEquals(second,s.rawWindow("SH600030").versionId());
  assertEquals(first,w.versionId());assertEquals("10",w.bars().get(59).close());
  f.jdbc().update("update market_data_version set status='UNAVAILABLE' where version_id=?",second);
  code("MARKET_VERSION_UNAVAILABLE",()->s.rawWindow("SH600030"));
 }
 @Test void rawWindowRejectsKnownStaleBeforeCollectionButAllowsUnknownCalendar(){
  var f=new MarketDatabaseFixture();var id=raw(f,"preflight","10",0L);var market=new MarketVersionRepository(f.jdbc());
  var later=Clock.fixed(Instant.parse("2026-10-09T14:00:00Z"),ZoneOffset.UTC);
  var service=new ForecastInputService(market,later,20);
  assertEquals("AVAILABLE",service.rawWindow("SH600030").state());
  assertEquals("UNKNOWN",service.rawWindow("SH600030").freshness());
  for(var d:calendar(bars("10")))f.jdbc().update("insert into market_calendar_day values(?,?,?)",id,d.date(),d.open());
  var holiday=new ForecastInputService(market,NOW,20);
  assertEquals("FRESH",holiday.rawWindow("SH600030").freshness());
  var beforeUpdate=new ForecastInputService(market,Clock.fixed(Instant.parse("2026-10-08T11:59:00Z"),ZoneOffset.UTC),20);
  assertEquals("FRESH",beforeUpdate.rawWindow("SH600030").freshness());
  var afterUpdate=new ForecastInputService(market,Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"),ZoneOffset.UTC),20);
  code("FORECAST_INPUT_STALE",()->afterUpdate.rawWindow("SH600030"));
  code("FORECAST_INPUT_STALE",()->service.rawWindow("SH600030"));
  assertEquals(id,f.jdbc().queryForObject("select version_id from instrument_data_head",String.class));
  assertEquals(0,f.jdbc().queryForObject("select count(*) from forecast_input_version",Integer.class));
 }
 @Test void sourceAdjustedPrecisionIsPreservedInsteadOfRejectedOrRounded(){
  var f=new MarketDatabaseFixture();var v=raw(f,"raw-precision","10",0L);var s=service(f);var c=candidate("input-precision",v);
  var exact=new BigDecimal("129.1391766600");var pairs=c.bars().stream().map(b->new ForecastCandidate.PairedBar(b.raw(),exact)).toList();
  var p=s.publish(new ForecastCandidate(c.operationId(),c.instrumentId(),v,c.provider(),c.definition(),c.fetchedAt(),c.sourcePayloadHash(),pairs,c.calendar()));
  assertEquals(0,exact.compareTo(f.jdbc().queryForObject("select backward_close from forecast_input_adjustment limit 1",BigDecimal.class)));
  assertEquals("12.913917666",s.window("SH600030",p.inputVersionId(),60,5).bars().get(0).sourceAdjustmentRatio());
 }
 @Test void publishesFrozenSixtyBarsAndFiveTradingDaysWithoutChangingRawHead(){
  var f=new MarketDatabaseFixture();var v=raw(f,"raw-1","10",0L);var s=service(f);var p=s.publish(candidate("input-1",v));
  var w=s.window("SH600030",p.inputVersionId(),60,5);
  assertEquals(60,w.bars().size());assertEquals(v,w.rawVersionId());assertEquals("10",w.originRawClose());
  assertEquals(List.of("2026-10-08","2026-10-09","2026-10-12","2026-10-13","2026-10-14"),w.targetDates());
  assertEquals("2",w.bars().get(0).sourceAdjustmentRatio());assertEquals("FRESH",w.freshness());
  assertEquals(v,f.jdbc().queryForObject("select version_id from instrument_data_head",String.class));
  assertEquals(60,f.jdbc().queryForObject("select count(*) from market_daily_bar",Integer.class));
 }
 @Test void mismatchMissingFactorAndFuturePricesAreRejected(){
  var f=new MarketDatabaseFixture();var v=raw(f,"raw-1","10",0L);var s=service(f);var c=candidate("mismatch",v);var changed=new ArrayList<>(c.bars());
  var b=changed.get(0).raw();changed.set(0,new ForecastCandidate.PairedBar(new RawBar(b.date(),b.open(),b.high(),b.low(),new BigDecimal("9"),b.volumeShares(),b.amountYuan(),1,null),new BigDecimal("20")));
  var bad=new ForecastCandidate(c.operationId(),c.instrumentId(),c.rawVersionId(),c.provider(),c.definition(),c.fetchedAt(),c.sourcePayloadHash(),changed,c.calendar());
  code("FORECAST_RAW_MISMATCH",()->s.publish(bad));
  changed=new ArrayList<>(c.bars());changed.set(0,new ForecastCandidate.PairedBar(b,null));
  var missing=new ForecastCandidate("missing",c.instrumentId(),v,c.provider(),c.definition(),c.fetchedAt(),c.sourcePayloadHash(),changed,c.calendar());
  code("FORECAST_INPUT_INVALID",()->s.publish(missing));
  changed=new ArrayList<>(c.bars());changed.set(59,new ForecastCandidate.PairedBar(new RawBar(LocalDate.of(2099,1,1),b.open(),b.high(),b.low(),b.close(),b.volumeShares(),b.amountYuan(),1,null),new BigDecimal("20")));
  var future=new ForecastCandidate("future",c.instrumentId(),v,c.provider(),c.definition(),c.fetchedAt(),c.sourcePayloadHash(),changed,c.calendar());
  code("FORECAST_INPUT_INVALID",()->s.publish(future));
  assertEquals(0,f.jdbc().queryForObject("select count(*) from forecast_input_version",Integer.class));
 }
 @Test void calendarGapAndMissingFutureTargetsAreRejected(){
  var f=new MarketDatabaseFixture();var v=raw(f,"raw-1","10",0L);var s=service(f);var c=candidate("gap",v);var gap=new ArrayList<>(c.calendar());gap.remove(5);
  code("FORECAST_CALENDAR_INVALID",()->s.publish(new ForecastCandidate(c.operationId(),c.instrumentId(),v,c.provider(),c.definition(),c.fetchedAt(),c.sourcePayloadHash(),c.bars(),gap)));
  var shortCal=c.calendar().stream().filter(d->!d.date().isAfter(LocalDate.of(2026,10,9))).toList();
  code("FORECAST_CALENDAR_INVALID",()->s.publish(new ForecastCandidate("short",c.instrumentId(),v,c.provider(),c.definition(),c.fetchedAt(),c.sourcePayloadHash(),c.bars(),shortCal)));
 }
 @Test void retriesAndDifferentOperationsDeduplicateButSameOperationCannotChange(){
  var f=new MarketDatabaseFixture();var v=raw(f,"raw-1","10",0L);var s=service(f);var c=candidate("input-1",v);var p=s.publish(c);
  assertTrue(s.publish(c).replayed());assertEquals(p.inputVersionId(),s.publish(candidate("input-2",v)).inputVersionId());
  var altered=candidate("input-1",v);var paired=altered.bars().stream().map(b->new ForecastCandidate.PairedBar(b.raw(),new BigDecimal("21"))).toList();
  code("FORECAST_OPERATION_CONFLICT",()->s.publish(new ForecastCandidate(altered.operationId(),altered.instrumentId(),v,altered.provider(),altered.definition(),altered.fetchedAt(),altered.sourcePayloadHash(),paired,altered.calendar())));
  assertEquals(1,f.jdbc().queryForObject("select count(*) from forecast_input_version",Integer.class));
 }
 @Test void rawHeadChangeCannotRewriteFrozenInputOrSelectItAsCurrent(){
  var f=new MarketDatabaseFixture();var v=raw(f,"raw-1","10",0L);var s=service(f);var p=s.publish(candidate("input-1",v));raw(f,"raw-2","11",1L);
  assertEquals(v,s.window("SH600030",p.inputVersionId(),60,5).rawVersionId());
  code("FORECAST_INPUT_NOT_AVAILABLE",()->s.window("SH600030",null,60,5));
  code("RESOURCE_NOT_FOUND",()->s.window("SZ000002",p.inputVersionId(),60,5));
 }
 @Test void newRequestsRejectStaleInputAndUnusableRawVersion(){
  var f=new MarketDatabaseFixture();var v=raw(f,"raw-1","10",0L);var s=service(f);var p=s.publish(candidate("input-1",v));
  var later=new ForecastInputService(new MarketVersionRepository(f.jdbc()),Clock.fixed(Instant.parse("2026-10-08T14:00:00Z"),ZoneOffset.UTC),20);
  code("FORECAST_INPUT_STALE",()->later.window("SH600030",null,60,5));
  assertEquals("STALE",later.window("SH600030",p.inputVersionId(),60,5).freshness());
  f.jdbc().update("update market_data_version set status='UNAVAILABLE' where version_id=?",v);
  code("MARKET_VERSION_UNAVAILABLE",()->s.window("SH600030",p.inputVersionId(),60,5));
 }
 @Test void concurrentSameContentCreatesOnlyOneCompleteSnapshot()throws Exception{
  var f=new MarketDatabaseFixture();var v=raw(f,"raw-1","10",0L);var s=service(f);var pool=Executors.newFixedThreadPool(2);var barrier=new CyclicBarrier(2);
  try{
   var a=pool.submit(()->{barrier.await();return s.publish(candidate("parallel-a",v)).inputVersionId();});
   var b=pool.submit(()->{barrier.await();return s.publish(candidate("parallel-b",v)).inputVersionId();});
   assertEquals(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
   assertEquals(60,f.jdbc().queryForObject("select count(*) from forecast_input_adjustment",Integer.class));
  }finally{pool.shutdownNow();}
 }
}
