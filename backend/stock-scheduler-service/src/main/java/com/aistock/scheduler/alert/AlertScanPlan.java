package com.aistock.scheduler.alert;

import static com.aistock.scheduler.alert.AlertMarketClient.*;
import com.aistock.scheduler.task.RevisionClient;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;import org.slf4j.LoggerFactory;

@Component public class AlertScanPlan {
 private static final Logger LOG=LoggerFactory.getLogger(AlertScanPlan.class);
 private final AlertScanRepository scans;private final AlertMarketClient market;private final RevisionClient revisions;
 private final Clock clock;private final String token;private final boolean enabled;private final int hour,pageSize;private final Set<String> instruments;
 public AlertScanPlan(AlertScanRepository scans,AlertMarketClient market,RevisionClient revisions,Clock clock,
  @Value("${aistock.internal.token:}")String token,@Value("${aistock.alert.plan-enabled:false}")boolean enabled,
  @Value("${aistock.alert.update-hour:20}")int hour,@Value("${aistock.alert.page-size:50}")int pageSize,
  @Value("${aistock.alert.instruments:SH600030,SH600276,SZ000002}")String instruments){
  if(hour<0||hour>23||pageSize<1||pageSize>50)throw AlertScanRepository.invalid();
  this.scans=scans;this.market=market;this.revisions=revisions;this.clock=clock;this.token=token;this.enabled=enabled;this.hour=hour;this.pageSize=pageSize;
  this.instruments=Set.copyOf(Arrays.asList(instruments.split(",")));if(this.instruments.isEmpty()||this.instruments.size()>50||this.instruments.stream().anyMatch(s->!AlertScanRepository.stock(s)))throw AlertScanRepository.invalid();
 }
 @Scheduled(fixedDelayString="${aistock.alert.scan-poll-ms:60000}") public synchronized void tick(){if(enabled&&!token.isBlank())runOnce();}
 public synchronized boolean runOnce(){
  AlertScanRepository.Cursor cursor=null;
  try{
   var claim=scans.claimCalendar(clock.instant());
   if(claim!=null){try{var fresh=market.refresh(claim.operationId(),claim.start(),claim.end());expected(fresh,clock.instant(),hour);scans.finishCalendar(claim,true,null,clock.instant());}catch(RuntimeException unavailable){scans.finishCalendar(claim,false,"CALENDAR_REFRESH_UNAVAILABLE",clock.instant());}}
   CalendarSnapshot calendar;LocalDate due;
   try{calendar=market.calendar();due=expected(calendar,clock.instant(),hour);scans.calendarReadState(true);}
   catch(RuntimeException unverified){scans.calendarReadState(false);throw unverified;}
   var now=clock.instant();var cutoff=cutoff(now,hour);
   var reconcile=scans.cursor("RECONCILE",now);
   cursor=reconcile.roundId()!=null||!now.isBefore(reconcile.nextAt())?reconcile:scans.cursor("EVENT",now);
   if(now.isBefore(cursor.nextAt()))return true;
   RevisionClient.Event event=cursor.eventId()==null?null:new RevisionClient.Event(cursor.eventId(),cursor.instrumentId(),cursor.eventRawVersion());
   if("EVENT".equals(cursor.kind())&&event==null){event=revisions.next(cursor.completedEventId());if(event==null)return true;}
   if(cursor.roundId()==null||!cutoff.equals(cursor.cutoffDate())||!due.equals(cursor.expectedDate())){
    if(!scans.begin(cursor,event,cutoff,due,clock.instant()))return false;cursor=scans.cursor(cursor.kind(),clock.instant());
   }
   var page=market.due(cursor.instrumentId(),cursor.afterId(),pageSize);
   var inputs=new ArrayList<AlertJobRecords.Input>();var boundaries=new HashMap<String,Boundary>();var unavailable=new HashSet<String>();String reason=null;
   for(var r:page.items()){
    if(!instruments.contains(r.instrumentId())){reason="ALERT_INSTRUMENT_NOT_CONFIGURED";continue;}
    if(!due.isAfter(r.effectiveAfterTradeDate()))continue;
    if(!boundaries.containsKey(r.instrumentId())&&!unavailable.contains(r.instrumentId())){
     try{var b=market.boundary(r.instrumentId());if(b==null||!r.instrumentId().equals(b.instrumentId())||b.rawVersionId()==null||b.asOf()==null||b.asOf().isBefore(clock.instant().minusSeconds(5))||b.asOf().isAfter(clock.instant().plusSeconds(5)))throw AlertScanRepository.invalid();boundaries.put(r.instrumentId(),b);}catch(RuntimeException missing){unavailable.add(r.instrumentId());}
    }
    if(unavailable.contains(r.instrumentId())){reason="ALERT_BOUNDARY_UNAVAILABLE";continue;}
    var boundary=boundaries.get(r.instrumentId());
    if(!due.equals(boundary.expectedCompletedTradeDate())||!due.equals(boundary.latestPublishedTradeDate())){reason="ALERT_RAW_LAGGING";continue;}
    inputs.add(new AlertJobRecords.Input(r.ruleId(),r.revision(),r.instrumentId(),due,boundary.rawVersionId(),cursor.roundId()));
   }
   now=clock.instant();if(!due.equals(expected(calendar,now,hour))||!cutoff.equals(cutoff(now,hour)))return false;
   return scans.publish(cursor,page,inputs,now,reason);
  }catch(RuntimeException unavailable){if(cursor!=null)scans.defer(cursor,"ALERT_PAGE_UNAVAILABLE",clock.instant());LOG.warn("Alert scan incomplete; saved cursor retained");return false;}
 }
 static LocalDate cutoff(Instant now,int hour){var local=now.atZone(ZoneId.of("Asia/Shanghai"));return local.toLocalDate().minusDays(local.getHour()<hour?1:0);}
 static LocalDate expected(CalendarSnapshot c,Instant now,int hour){
  var cutoff=cutoff(now,hour);
  if(c==null||c.versionId()==null||!"baostock".equals(c.provider())||c.start()==null||c.end()==null||c.fetchedAt()==null||c.fetchedAt().isBefore(now.minus(Duration.ofDays(7)))||c.fetchedAt().isAfter(now.plusSeconds(5))||c.start().isAfter(cutoff.minusDays(365))||c.end().isBefore(cutoff)||c.end().isBefore(c.start())||java.time.temporal.ChronoUnit.DAYS.between(c.start(),c.end())>=400||c.days().size()!=java.time.temporal.ChronoUnit.DAYS.between(c.start(),c.end())+1)throw AlertScanRepository.invalid();
  var date=c.start();LocalDate due=null;var text=new StringBuilder();
  for(var d:c.days()){if(d==null||d.open()==null||!date.equals(d.date()))throw AlertScanRepository.invalid();text.append(date).append(':').append(d.open()?'1':'0').append('\n');if(d.open()&&!date.isAfter(cutoff))due=date;date=date.plusDays(1);}
  try{if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8))).equals(c.sourcePayloadHash()))throw AlertScanRepository.invalid();}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 unavailable");}
  if(due==null)throw AlertScanRepository.invalid();return due;
 }
}
