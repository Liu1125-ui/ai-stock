package com.aistock.market.valuation;
import com.aistock.common.BusinessException;
import com.aistock.market.calendar.CalendarService;
import com.aistock.market.calendar.CalendarRecords;
import java.time.*;import java.time.temporal.ChronoUnit;import java.util.*;
import org.springframework.http.HttpStatus;import org.springframework.stereotype.Service;
import static com.aistock.market.valuation.ValuationQuoteRecords.*;
@Service public class ValuationQuoteService {
 private final ValuationQuoteRepository repo;private final CalendarService calendars;private final Clock clock;
 public ValuationQuoteService(ValuationQuoteRepository repo,CalendarService calendars,Clock clock){this.repo=repo;this.calendars=calendars;this.clock=clock;}
 public QuoteBatch batch(QuoteRequest r){return com.aistock.market.marketlifecycle.MarketEvidenceGate.local(()->{
  var now=clock.instant().atZone(ZoneId.of("Asia/Shanghai"));var max=now.toLocalTime().isBefore(LocalTime.of(20,0))?now.toLocalDate().minusDays(1):now.toLocalDate();
  if(r==null||r.asOfDate()==null||r.asOfDate().isBefore(LocalDate.of(1990,1,1))||r.asOfDate().isAfter(max)||r.instrumentIds()==null||r.instrumentIds().isEmpty()||r.instrumentIds().size()>100)throw invalid();
  var ids=List.copyOf(r.instrumentIds());if(ids.stream().anyMatch(id->id==null||!id.matches("(SH|SZ)[0-9]{6}"))||new HashSet<>(ids).size()!=ids.size())throw invalid();
  com.aistock.market.marketlifecycle.MarketEvidenceGate.stocks(ids);return repo.read(()->new QuoteBatch(r.asOfDate(),clock.instant(),ids.stream().map(id->quote(id,r.asOfDate())).toList()));
 });}
 private Quote quote(String id,LocalDate cutoff){
  var head=repo.head(id).orElse(null);if(head==null)return unavailable(id,"NO_DATA",null,null,null,null,null,null,false);
  var price=repo.price(head.versionId(),cutoff).orElse(null);if(price==null)return unavailable(id,"NO_VALID_CLOSE",head.versionId(),head.contentHash(),null,null,null,null,false);
  boolean retained=repo.retained(head.versionId(),cutoff);String freshness="UNKNOWN";UUID calendarId=null;String hash=null;LocalDate checked=null;
  var calendar=calendars.covering(price.date(),cutoff).orElse(null);
  if(calendar!=null&&continuous(calendar.candidate())){
   var candidate=calendar.candidate();calendarId=calendar.versionId();hash=candidate.sourcePayloadHash();checked=cutoff;
   boolean open=candidate.days().stream().anyMatch(d->d.date().equals(price.date())&&Boolean.TRUE.equals(d.open()));
   var latest=candidate.days().stream().filter(d->!d.date().isAfter(cutoff)&&Boolean.TRUE.equals(d.open())).map(CalendarRecords.Day::date).max(Comparator.naturalOrder()).orElse(null);
   if(!open||latest==null||price.date().isAfter(latest))return unavailable(id,"CALENDAR_CONFLICT",head.versionId(),head.contentHash(),price.date(),calendarId,hash,checked,retained);
   freshness=price.date().equals(latest)?"FRESH":"STALE";
  }
  return new Quote(id,"AVAILABLE",null,price.close().setScale(6).toPlainString(),price.date(),head.versionId(),head.contentHash(),"baostock","RAW",freshness,calendarId,hash,checked,retained);
 }
 private boolean continuous(CalendarRecords.Candidate c){
  if(c==null||c.start()==null||c.end()==null||c.days()==null||c.end().isBefore(c.start())||c.days().size()!=ChronoUnit.DAYS.between(c.start(),c.end())+1)return false;
  var date=c.start();for(var d:c.days()){if(d==null||d.open()==null||!date.equals(d.date()))return false;date=date.plusDays(1);}
  return CalendarService.hash(c.days()).equals(c.sourcePayloadHash());
 }
 private Quote unavailable(String id,String reason,UUID version,String content,LocalDate date,UUID calendar,String hash,LocalDate checked,boolean retained){return new Quote(id,"UNAVAILABLE",reason,null,date,version,content,"baostock","RAW","UNKNOWN",calendar,hash,checked,retained);}
 private BusinessException invalid(){return new BusinessException("PORTFOLIO_QUOTE_REQUEST_INVALID","持仓报价日期或证券集合不符合要求",HttpStatus.BAD_REQUEST);}
}
