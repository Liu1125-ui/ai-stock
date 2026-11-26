package com.aistock.market.comparison;
import com.aistock.market.ingestion.*;import com.aistock.market.quotation.*;import com.aistock.market.support.MarketDatabaseFixture;import com.aistock.market.version.MarketVersionRepository;import java.time.*;import java.util.*;import java.math.BigDecimal;import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;import static com.aistock.market.comparison.ComparisonEvidenceRecords.*;
@Tag("mysql") class ComparisonEvidenceOptionsMysqlTest {
 @Test void coveredYearRemainsSelectableAfterShortPublication(){var f=new MarketDatabaseFixture();var end=LocalDate.parse("2026-10-09");var clock=Clock.fixed(Instant.parse("2026-10-12T00:00:00Z"),ZoneOffset.UTC);var market=new MarketVersionRepository(f.jdbc());var svc=new ComparisonEvidenceService(market,clock);var ids=List.of("SH600030","SH600276");UUID expected=null;
 for(String stock:ids){var b=new RawBar(end,BigDecimal.TEN,BigDecimal.TEN,BigDecimal.TEN,BigDecimal.TEN,new BigDecimal("100"),new BigDecimal("1000"),1,null);var raw=new PublicationService(market,new CandidateValidator()).publish(new MarketCandidate("raw-"+stock,stock,stock,stock,true,"ACTIVE","baostock","RAW",clock.instant(),null,"a".repeat(64),List.of(b),List.of(new TradingDay(end,true)),0L));var calendar=end.minusYears(1).datesUntil(end.plusDays(1)).map(d->new TradingDay(d,true)).toList();var c=new Candidate("year-"+stock,stock,UUID.fromString(raw.versionId()),end.minusYears(1),end,"baostock",ComparisonEvidenceValidator.DEFINITION,clock.instant(),"b".repeat(64),List.of(new Point(end,BigDecimal.TEN,BigDecimal.TEN,new BigDecimal("100"),1)),calendar);var saved=svc.publish(c);if(stock.equals(ids.get(0))){expected=saved.evidenceId();f.jdbc().update("UPDATE comparison_evidence_version SET created_at='2026-10-11' WHERE evidence_id=?",expected.toString());svc.publish(new Candidate("short",stock,UUID.fromString(raw.versionId()),end.minusMonths(3),end,"baostock",ComparisonEvidenceValidator.DEFINITION,clock.instant(),"c".repeat(64),c.points(),calendar.stream().filter(d->!d.date().isBefore(end.minusMonths(3))).toList()));}}
 var options=svc.options(ids,"1Y");assertTrue(options.available());assertEquals(expected,options.stocks().get(0).evidenceId());assertEquals(end,options.stocks().get(0).rawHeadDate());assertFalse(svc.options(List.of("SH600030","SZ000002"),"3M").available());
 }
 @Test void rollingYearUsesRetainedOldVersionsUntilAllStocksAdvance(){
  var f=new MarketDatabaseFixture();var clock=Clock.fixed(Instant.parse("2026-10-14T00:00:00Z"),ZoneOffset.UTC);
  var market=new MarketVersionRepository(f.jdbc());var svc=new ComparisonEvidenceService(market,clock);
  var ids=List.of("SH600030","SH600276","SZ000002");var oldEnd=LocalDate.of(2026,10,9);var newEnd=LocalDate.of(2026,10,13);
  var olds=new HashMap<String,UUID>();var raws=new HashMap<String,UUID>();
  for(var stock:ids){
   var from=oldEnd.minusYears(1);var days=from.datesUntil(newEnd.plusDays(1)).map(d->new TradingDay(d,true)).toList();
   var bars=days.stream().map(d->new RawBar(d.date(),BigDecimal.TEN,BigDecimal.TEN,BigDecimal.TEN,BigDecimal.TEN,new BigDecimal("100"),new BigDecimal("1000"),1,null)).toList();
   var raw=new PublicationService(market,new CandidateValidator()).publish(new MarketCandidate("raw-"+stock,stock,stock,stock,true,"ACTIVE","baostock","RAW",clock.instant(),null,"a".repeat(64),bars,days,0L));
   var rawId=UUID.fromString(raw.versionId());raws.put(stock,rawId);
   olds.put(stock,svc.publish(new Candidate("old-"+stock,stock,rawId,from,oldEnd,"baostock",ComparisonEvidenceValidator.DEFINITION,clock.instant(),"b".repeat(64),
    bars.stream().filter(b->!b.date().isAfter(oldEnd)).map(b->new Point(b.date(),b.close(),b.close(),b.volumeShares(),1)).toList(),days.stream().filter(d->!d.date().isAfter(oldEnd)).toList())).evidenceId());
  }
  for(int i=0;i<3;i++){
   var stock=ids.get(i);var from=newEnd.minusYears(1);var days=from.datesUntil(newEnd.plusDays(1)).map(d->new TradingDay(d,true)).toList();
   svc.publish(new Candidate("new-"+stock,stock,raws.get(stock),from,newEnd,"baostock",ComparisonEvidenceValidator.DEFINITION,clock.instant(),"c".repeat(64),
    days.stream().map(d->new Point(d.date(),BigDecimal.TEN,BigDecimal.TEN,new BigDecimal("100"),1)).toList(),days));
   var options=svc.options(ids,"1Y");assertTrue(options.available());
   if(i<2){assertEquals(oldEnd,options.to());assertEquals(oldEnd.minusYears(1),options.from());for(var chosen:options.stocks())assertEquals(olds.get(chosen.instrumentId()),chosen.evidenceId());}
   else {assertEquals(newEnd,options.to());assertEquals(newEnd.minusYears(1),options.from());}
  }
 }
}

