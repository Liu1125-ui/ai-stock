package com.aistock.market.valuation;
import com.aistock.market.calendar.*;
import com.aistock.market.ingestion.*;
import com.aistock.market.quotation.*;
import com.aistock.market.support.MarketDatabaseFixture;
import com.aistock.market.version.MarketVersionRepository;
import com.aistock.common.BusinessException;
import java.time.*;import java.util.*;import java.math.BigDecimal;
import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
@Tag("mysql") class ValuationQuoteMysqlTest {
 final Clock clock=Clock.fixed(Instant.parse("2026-09-28T13:00:00Z"),ZoneOffset.UTC);
 MarketDatabaseFixture f;CalendarCollector collector;CalendarService calendars;ValuationQuoteRepository repo;ValuationQuoteService service;
 @BeforeEach void setup(){f=new MarketDatabaseFixture();collector=mock(CalendarCollector.class);calendars=new CalendarService(f.jdbc(),collector,clock,7);repo=new ValuationQuoteRepository(f.jdbc());service=new ValuationQuoteService(repo,calendars,clock);}
 RawBar bar(String date,String price,int status){var p=new BigDecimal(price);return new RawBar(LocalDate.parse(date),p,p,p,p,BigDecimal.TEN,BigDecimal.TEN,status,null);}
 String publish(String stock,List<RawBar> bars){return new PublicationService(new MarketVersionRepository(f.jdbc()),new CandidateValidator()).publish(new MarketCandidate(UUID.randomUUID().toString(),stock,stock,stock,false,"UNKNOWN","baostock","RAW",clock.instant().plusSeconds(172800),"fixture","a".repeat(64),bars,List.of(),null)).versionId();}
 ValuationQuoteRecords.Quote quote(String stock,String asOf){return service.batch(new ValuationQuoteRecords.QuoteRequest(LocalDate.parse(asOf),List.of(stock))).items().get(0);}
 void calendar(boolean conflict){
  var days=new ArrayList<CalendarRecords.Day>();for(var d=LocalDate.parse("2026-09-20");!d.isAfter(LocalDate.parse("2026-09-28"));d=d.plusDays(1))days.add(new CalendarRecords.Day(d,conflict?false:d.getDayOfWeek()!=DayOfWeek.SATURDAY&&d.getDayOfWeek()!=DayOfWeek.SUNDAY));
  var c=new CalendarRecords.Candidate("baostock",days.get(0).date(),days.get(days.size()-1).date(),clock.instant(),CalendarService.hash(days),days);
  new CalendarService(f.jdbc(),r->c,clock,7).refresh(new CalendarRecords.Request(UUID.randomUUID().toString(),c.start(),c.end()));
 }
 @Test void fixedCutoffUsesActualLastTradingCloseAndRetainsSuspensionEvidence(){
  var version=publish("SH600030",List.of(bar("2026-09-24","25",1),bar("2026-09-25","26",1),bar("2026-09-28","26",0),bar("2026-09-29","999",1)));
  var q=quote("SH600030","2026-09-28");assertEquals("AVAILABLE",q.status());assertEquals("26.000000",q.close());assertEquals(LocalDate.parse("2026-09-25"),q.priceDate());assertEquals(UUID.fromString(version),q.versionId());assertEquals("RAW",q.priceBasis());assertTrue(q.retainedPrice());assertEquals("UNKNOWN",q.freshness());verifyNoInteractions(collector);
  calendar(false);assertEquals("STALE",quote("SH600030","2026-09-28").freshness());assertEquals("FRESH",quote("SH600030","2026-09-27").freshness());
 }
 @Test void missingUnavailableAndOtherBasisNeverBecomeZeroPrices(){
  assertEquals("NO_DATA",quote("SH600030","2026-09-28").reason());
  var version=publish("SH600030",List.of(bar("2026-09-28","25",0)));assertEquals("NO_VALID_CLOSE",quote("SH600030","2026-09-28").reason());
  f.jdbc().update("update market_data_version set status='STAGING' where version_id=?",version);assertNull(quote("SH600030","2026-09-28").close());
  f.jdbc().update("update market_data_version set status='AVAILABLE' where version_id=?",version);f.jdbc().update("update instrument_data_head set price_basis='QFQ' where version_id=?",version);assertEquals("NO_DATA",quote("SH600030","2026-09-28").reason());
 }
 @Test void continuousCalendarRequiredButUnknownCalendarDoesNotDiscardValidClose(){
  publish("SH600030",List.of(bar("2026-09-25","25",1)));calendar(false);
  var q=quote("SH600030","2026-09-27");assertEquals("FRESH",q.freshness());assertNotNull(q.calendarVersionId());assertEquals(64,q.calendarHash().length());
  f.jdbc().update("update independent_calendar_version set fetched_at=date_sub(fetched_at,interval 8 day)");assertEquals("UNKNOWN",quote("SH600030","2026-09-27").freshness());assertNotNull(quote("SH600030","2026-09-27").close());
 }
 @Test void explicitClosedPriceDayIsUnavailable(){publish("SH600030",List.of(bar("2026-09-25","25",1)));calendar(true);var q=quote("SH600030","2026-09-28");assertEquals("CALENDAR_CONFLICT",q.reason());assertNull(q.close());}
 @Test void rejectsDuplicateCodesFutureCutoffAndOverLimitWithoutSourceCalls(){
  var ids=java.util.stream.IntStream.range(0,100).mapToObj(i->"SH"+String.format("%06d",i)).toList();var batch=service.batch(new ValuationQuoteRecords.QuoteRequest(LocalDate.parse("2026-09-28"),ids));assertEquals(100,batch.items().size());assertEquals(new HashSet<>(ids),batch.items().stream().map(ValuationQuoteRecords.Quote::instrumentId).collect(java.util.stream.Collectors.toSet()));
  assertThrows(BusinessException.class,()->service.batch(new ValuationQuoteRecords.QuoteRequest(LocalDate.parse("2026-09-28"),List.of("SH600030","SH600030"))));
  assertThrows(BusinessException.class,()->quote("SH600030","2026-09-29"));assertThrows(BusinessException.class,()->quote("forged","2026-09-28"));
  var early=new ValuationQuoteService(repo,calendars,Clock.fixed(Instant.parse("2026-09-28T11:59:59Z"),ZoneOffset.UTC));assertThrows(BusinessException.class,()->early.batch(new ValuationQuoteRecords.QuoteRequest(LocalDate.parse("2026-09-28"),List.of("SH600030"))));verifyNoInteractions(collector);
 }
 @Test void readTransactionCannotMixHeadsDuringConcurrentPublication()throws Exception{
  String first=publish("SH600030",List.of(bar("2026-09-25","25",1)));
  var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
  try{repo.read(()->{
   assertEquals(UUID.fromString(first),quote("SH600030","2026-09-28").versionId());
   try{executor.submit(()->publish("SH600030",List.of(bar("2026-09-25","30",1)))).get(4,java.util.concurrent.TimeUnit.SECONDS);}catch(Exception e){throw new AssertionError(e);}
   assertEquals(UUID.fromString(first),quote("SH600030","2026-09-28").versionId());return null;
  });assertEquals("30.000000",quote("SH600030","2026-09-28").close());}finally{executor.shutdownNow();}
 }
 @Test void discontinuousOrFutureCalendarIsUnknownAndHistoricalCutoffRemainsFixed()throws Exception{
  publish("SH600030",List.of(bar("2026-09-25","25",1)));calendar(false);
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
  var payload=f.jdbc().queryForObject("select candidate_json from independent_calendar_version",String.class);
  var tree=mapper.readTree(payload);((com.fasterxml.jackson.databind.node.ArrayNode)tree.get("days")).remove(2);
  f.jdbc().update("update independent_calendar_version set candidate_json=?",mapper.writeValueAsString(tree));
  assertEquals("UNKNOWN",quote("SH600030","2026-09-27").freshness());assertNotNull(quote("SH600030","2026-09-27").close());
  f.jdbc().update("update independent_calendar_version set candidate_json=?,fetched_at=?",payload,java.sql.Timestamp.from(clock.instant().plusSeconds(60)));
  assertEquals("UNKNOWN",quote("SH600030","2026-09-27").freshness());
  var later=new ValuationQuoteService(repo,calendars,Clock.fixed(clock.instant().plusSeconds(86400),ZoneOffset.UTC));
  assertEquals(LocalDate.parse("2026-09-27"),later.batch(new ValuationQuoteRecords.QuoteRequest(LocalDate.parse("2026-09-27"),List.of("SH600030"))).asOfDate());
  var tooMany=java.util.stream.IntStream.range(0,101).mapToObj(i->"SH"+String.format("%06d",i)).toList();
  assertThrows(BusinessException.class,()->service.batch(new ValuationQuoteRecords.QuoteRequest(LocalDate.parse("2026-09-28"),tooMany)));
  verifyNoInteractions(collector);
 }
}
