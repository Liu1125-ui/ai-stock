package com.aistock.market.alert;
import com.aistock.common.BusinessException;import com.aistock.market.version.MarketVersionRepository;import com.aistock.market.quotation.*;import java.time.*;import java.util.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import org.springframework.stereotype.Service;import org.springframework.beans.factory.annotation.Value;import org.springframework.http.HttpStatus;
@Service public class AlertBoundaryService {
 public record Boundary(String instrumentId,String provider,UUID rawVersionId,LocalDate latestPublishedTradeDate,LocalDate expectedCompletedTradeDate,LocalDate effectiveAfterTradeDate,String calendarHash,Instant asOf,String definition){}
 private final MarketVersionRepository repo;private final com.aistock.market.calendar.CalendarService calendar;private final Clock clock;private final int hour;
 public AlertBoundaryService(MarketVersionRepository repo,com.aistock.market.calendar.CalendarService calendar,Clock clock,@Value("${aistock.market.update-hour:20}")int hour){this.repo=repo;this.calendar=calendar;this.clock=clock;this.hour=hour;}
 public Boundary get(String id){if(id==null||!id.matches("(SH|SZ)[0-9]{6}"))throw unavailable();var rows=repo.jdbc().queryForList("select v.version_id from instrument_data_head h join market_data_version v on v.version_id=h.version_id join instrument i on i.instrument_id=h.instrument_id where h.instrument_id=? and h.provider='baostock' and h.price_basis='RAW' and v.status='AVAILABLE' and i.listing_status='ACTIVE' and i.demo_only=false",id);if(rows.size()!=1)throw unavailable();var version=UUID.fromString((String)rows.get(0).get("version_id"));var bars=repo.bars(version.toString());if(bars.isEmpty())throw unavailable();var now=clock.instant();var local=now.atZone(ZoneId.of("Asia/Shanghai"));var cutoff=local.toLocalDate().minusDays(local.getHour()<hour?1:0);var selected=calendar.covering(bars.get(bars.size()-1).date(),cutoff).map(snapshot->snapshot.candidate().days().stream().map(day->new TradingDay(day.date(),day.open())).toList()).orElseGet(()->repo.calendar(version.toString()));return resolve(id,version,bars,selected,now,hour);}
 public static Boundary resolve(String id,UUID version,List<RawBar> bars,List<TradingDay> calendar,Instant now,int hour){
  if(hour<0||hour>23||bars==null||bars.isEmpty()||calendar==null||calendar.isEmpty())throw unavailable();
  var local=now.atZone(ZoneId.of("Asia/Shanghai"));var cutoff=local.toLocalDate().minusDays(local.getHour()<hour?1:0);var last=bars.get(bars.size()-1);if(last.tradeStatus()!=1||last.date().isAfter(local.toLocalDate()))throw unavailable();
  var days=new TreeMap<LocalDate,Boolean>();for(var d:calendar)if(d==null||days.put(d.date(),d.open())!=null)throw unavailable();
  LocalDate expected=null;var start=days.firstKey();if(start.isAfter(cutoff)||days.lastKey().isBefore(cutoff))throw unavailable();
  for(var day=start;!day.isAfter(cutoff);day=day.plusDays(1)){if(!days.containsKey(day))throw unavailable();if(days.get(day))expected=day;}
  if(expected==null||!Boolean.TRUE.equals(days.get(last.date())))throw unavailable();
  String hash;try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(days.toString().getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw unavailable();}
  return new Boundary(id,"baostock",version,last.date(),expected,last.date().isAfter(expected)?last.date():expected,hash,now,"alert-boundary-v1");
 }
 static BusinessException unavailable(){return new BusinessException("ALERT_BOUNDARY_UNAVAILABLE","预警生效日期暂无法核验，请稍后重试；已有规则保留",HttpStatus.SERVICE_UNAVAILABLE);}
}
