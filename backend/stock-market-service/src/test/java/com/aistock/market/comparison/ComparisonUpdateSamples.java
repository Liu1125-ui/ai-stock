package com.aistock.market.comparison;
import com.aistock.market.ingestion.*;
import com.aistock.market.quotation.*;
import com.aistock.market.calendar.*;
import com.aistock.market.support.MarketDatabaseFixture;
import com.aistock.market.version.MarketVersionRepository;
import java.time.*;import java.math.BigDecimal;import java.util.*;
import static org.mockito.Mockito.*;

final class ComparisonUpdateSamples {
 static final LocalDate END=LocalDate.of(2026,10,13), START=END.minusYears(1);
 static final Clock NOW=Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"),ZoneOffset.UTC);
 static List<TradingDay> calendar(LocalDate from,LocalDate to){
  return from.datesUntil(to.plusDays(1)).map(d->new TradingDay(d,
   d.getDayOfWeek()!=DayOfWeek.SATURDAY&&d.getDayOfWeek()!=DayOfWeek.SUNDAY)).toList();
 }
 static List<RawBar> bars(LocalDate from,LocalDate to,String price,boolean suspendEnd){
  BigDecimal p=new BigDecimal(price),v=new BigDecimal("100");
  return calendar(from,to).stream().filter(TradingDay::open).map(d->new RawBar(d.date(),p,p,p,p,
   v,p.multiply(v),suspendEnd&&d.date().equals(to)?0:1,null)).toList();
 }
 static UUID raw(MarketDatabaseFixture f,String op,LocalDate from,LocalDate to,String price,
   boolean suspended,long revision,Clock clock){
  return UUID.fromString(new PublicationService(new MarketVersionRepository(f.jdbc()),new CandidateValidator()).publish(
   new MarketCandidate(op,"SH600030","SH600030","中信证券",true,"ACTIVE","baostock","RAW",
    clock.instant(),null,"a".repeat(64),bars(from,to,price,suspended),calendar(from,to),revision)).versionId());
 }
 static String rawHash(MarketDatabaseFixture f,UUID id){
  return f.jdbc().queryForObject("select content_hash from market_data_version where version_id=?",String.class,id.toString());
 }
 static CalendarService validCalendar(LocalDate from,LocalDate to,Clock clock){
  var s=mock(CalendarService.class);
  var days=calendar(from,to).stream().map(d->new CalendarRecords.Day(d.date(),d.open())).toList();
  var c=new CalendarRecords.Candidate("baostock",from,to,clock.instant(),CalendarService.hash(days),days);
  when(s.covering(any(),any())).thenReturn(Optional.of(new CalendarRecords.Snapshot(UUID.randomUUID(),c)));
  return s;
 }
 static ComparisonUpdateRecords.Request request(MarketDatabaseFixture f,UUID id,LocalDate from,LocalDate to,Clock clock){
  return new ComparisonUpdateRecords.Request(UUID.randomUUID(),"SH600030",id,rawHash(f,id),from,to,
   ComparisonEvidenceValidator.DEFINITION,clock.instant().plusSeconds(90));
 }
 static ComparisonEvidenceRecords.Candidate candidate(ComparisonUpdateRecords.Request r,String price,String adjusted,Clock clock){
  return new ComparisonEvidenceRecords.Candidate(r.operationId().toString(),r.instrumentId(),r.rawVersionId(),
   r.start(),r.end(),"baostock",r.definition(),clock.instant(),"b".repeat(64),
   bars(r.start(),r.end(),price,false).stream().map(b->new ComparisonEvidenceRecords.Point(b.date(),b.close(),
    new BigDecimal(adjusted),b.volumeShares(),b.tradeStatus())).toList(),calendar(r.start(),r.end()));
 }
 static final class MutableClock extends Clock {
  private final java.util.concurrent.atomic.AtomicReference<Instant> value;private final ZoneId zone;
  MutableClock(Instant now){this(new java.util.concurrent.atomic.AtomicReference<>(now),ZoneOffset.UTC);}
  private MutableClock(java.util.concurrent.atomic.AtomicReference<Instant> value,ZoneId zone){this.value=value;this.zone=zone;}
  void advance(Duration d){value.updateAndGet(t->t.plus(d));}
  public Instant instant(){return value.get();}
  public ZoneId getZone(){return zone;}
  public Clock withZone(ZoneId z){return new MutableClock(value,z);}
 }
}
