package com.aistock.scheduler.alert;

import static com.aistock.scheduler.alert.AlertMarketClient.*;
import com.aistock.common.BusinessException;
import com.aistock.scheduler.task.RevisionClient;
import java.time.*;
import java.sql.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Repository public class AlertScanRepository {
 private final JdbcTemplate jdbc;
 private final AlertJobRepository jobs;
 private final TransactionTemplate tx;
 private final int reconcileMinutes,maxActive,updateHour;
 public AlertScanRepository(JdbcTemplate jdbc,AlertJobRepository jobs,
   @Value("${aistock.alert.reconcile-minutes:60}")int reconcileMinutes,
   @Value("${aistock.alert.max-active-jobs:1000}")int maxActive,
   @Value("${aistock.alert.update-hour:20}")int updateHour){
  if(reconcileMinutes<1||reconcileMinutes>1440||maxActive<1||maxActive>100000||updateHour<0||updateHour>23)throw invalid();
  this.jdbc=jdbc;this.jobs=jobs;this.reconcileMinutes=reconcileMinutes;this.maxActive=maxActive;this.updateHour=updateHour;
  tx=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));
  tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);tx.setTimeout(5);
 }
 public Cursor cursor(String kind,Instant now){checkKind(kind);return tx.execute(s->{
  jdbc.update("insert into alert_scan_cursor(kind,completed_event_id,revision,next_at,updated_at) values(?,0,0,?,?) on duplicate key update kind=kind",kind,ts(now),ts(now));return locked(kind);
 });}
 public boolean begin(Cursor c,RevisionClient.Event event,LocalDate cutoff,LocalDate expected,Instant now){
  if(c==null||expected==null||cutoff==null||expected.isAfter(cutoff)||!cutoff.equals(AlertScanPlan.cutoff(now,updateHour)))throw invalid();
  if("EVENT".equals(c.kind())&&(event==null||event.eventId()<=c.completedEventId()||!stock(event.instrumentId())||!uuid(event.rawVersionId())))throw invalid();
  if("RECONCILE".equals(c.kind())&&event!=null)throw invalid();
  return Boolean.TRUE.equals(tx.execute(s->{
   if(!locked(c.kind()).equals(c))return false;
   if(c.roundId()!=null){
    if(c.cutoffDate().equals(cutoff)&&c.expectedDate().equals(expected))return false;
    if("EVENT".equals(c.kind())&&(!Objects.equals(c.eventId(),event.eventId())||!Objects.equals(c.instrumentId(),event.instrumentId())||!Objects.equals(c.eventRawVersion(),event.rawVersionId())))throw invalid();
   }else if("RECONCILE".equals(c.kind())&&now.isBefore(c.nextAt()))return false;
   jdbc.update("update alert_scan_cursor set event_id=?,instrument_id=?,event_raw_version=?,round_id=?,cutoff_date=?,expected_date=?,after_id=null,last_reason=null,revision=revision+1,next_at=?,updated_at=? where kind=?",
    event==null?null:event.eventId(),event==null?null:event.instrumentId(),event==null?null:event.rawVersionId(),UUID.randomUUID().toString(),cutoff,expected,ts(now),ts(now),c.kind());return true;
  }));
 }
 public boolean publish(Cursor c,Page page,List<AlertJobRecords.Input> inputs,Instant now,String reason){
  validate(c,page,inputs,reason);
  return Boolean.TRUE.equals(tx.execute(s->{
   if(!locked(c.kind()).equals(c)||!c.cutoffDate().equals(AlertScanPlan.cutoff(now,updateHour)))return false;
   jdbc.update("insert into alert_scan_budget(id) values(1) on duplicate key update id=id");
   jdbc.queryForObject("select id from alert_scan_budget where id=1 for update",Integer.class);
   var active=jdbc.queryForObject("select count(*) from alert_job where active_key is not null",Integer.class);
   if(active==null||active+inputs.size()>maxActive)return false;
   for(var input:inputs)jobs.enqueue(input);
   if(page.nextCursor()==null){
    jdbc.update("update alert_scan_cursor set completed_event_id=coalesce(event_id,completed_event_id),event_id=null,instrument_id=null,event_raw_version=null,round_id=null,cutoff_date=null,expected_date=null,after_id=null,last_reason=?,revision=revision+1,next_at=?,completed_at=?,updated_at=? where kind=?",reason,ts(now.plusSeconds("RECONCILE".equals(c.kind())?reconcileMinutes*60L:0)),ts(now),ts(now),c.kind());
   }else jdbc.update("update alert_scan_cursor set after_id=?,last_reason=?,revision=revision+1,next_at=?,updated_at=? where kind=?",page.nextCursor(),reason,ts(now),ts(now),c.kind());
   return true;
  }));
 }
 public void defer(Cursor c,String reason,Instant now){if(reason==null||!reason.matches("[A-Z0-9_]{1,64}"))throw invalid();tx.executeWithoutResult(s->{if(!locked(c.kind()).equals(c))return;jdbc.update("update alert_scan_cursor set last_reason=?,next_at=?,revision=revision+1,updated_at=? where kind=?",reason,ts(now.plusSeconds(60)),ts(now),c.kind());});}
 public CalendarClaim claimCalendar(Instant now){return tx.execute(s->{
  var today=now.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
  jdbc.update("insert into alert_calendar_refresh(id,next_at,revision) values(1,?,0) on duplicate key update id=id",ts(now));
  var r=jdbc.queryForMap("select * from alert_calendar_refresh where id=1 for update");
  var lease=(Timestamp)r.get("lease_until");if(lease!=null&&now.isBefore(lease.toInstant()))return null;
  if(r.get("success_day")!=null&&today.equals(((java.sql.Date)r.get("success_day")).toLocalDate()))return null;
  if(now.isBefore(((Timestamp)r.get("next_at")).toInstant()))return null;
  boolean sameDay=r.get("request_day")!=null&&today.equals(((java.sql.Date)r.get("request_day")).toLocalDate());
  var op=sameDay?UUID.fromString((String)r.get("operation_id")):UUID.randomUUID();var owner=UUID.randomUUID();var until=now.plusSeconds(120).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
  jdbc.update("update alert_calendar_refresh set request_day=?,operation_id=?,range_start=?,range_end=?,owner_token=?,lease_until=?,revision=revision+1 where id=1",today,op.toString(),today.minusDays(366),today,owner.toString(),ts(until));
  return new CalendarClaim(op,today.minusDays(366),today,owner,until);
 });}
 public boolean finishCalendar(CalendarClaim c,boolean success,String reason,Instant now){return Boolean.TRUE.equals(tx.execute(s->{
  var r=jdbc.queryForMap("select * from alert_calendar_refresh where id=1 for update");
  if(!c.owner().toString().equals(r.get("owner_token"))||!c.operationId().toString().equals(r.get("operation_id"))||!now.isBefore(c.leaseUntil()))return false;
  if(reason!=null&&!reason.matches("[A-Z0-9_]{1,64}"))throw invalid();
  jdbc.update("update alert_calendar_refresh set success_day=?,owner_token=null,lease_until=null,last_reason=?,next_at=?,revision=revision+1 where id=1",success?c.end():null,reason,ts(success?c.end().plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant():now.plusSeconds(300)));return true;
 }));}
 /** Record the inspected snapshot state without changing the refresh lease or retry schedule. */
 public void calendarReadState(boolean verified){
  if(verified)jdbc.update("update alert_calendar_refresh set last_reason=null,revision=revision+1 where id=1 and last_reason='CALENDAR_SNAPSHOT_UNVERIFIED'");
  else jdbc.update("update alert_calendar_refresh set last_reason='CALENDAR_SNAPSHOT_UNVERIFIED',revision=revision+1 where id=1");
 }
 private void validate(Cursor c,Page p,List<AlertJobRecords.Input> inputs,String reason){
  if(c==null||c.roundId()==null||p==null||p.items().size()>50||inputs==null||inputs.size()>p.items().size()||reason!=null&&!reason.matches("[A-Z0-9_]{1,64}"))throw invalid();
  String previous=c.afterId()==null?"":c.afterId();var refs=new HashMap<UUID,RuleRef>();
  for(var r:p.items()){
   if(r==null||r.ruleId()==null||r.ownerId()==null||r.ruleId().toString().compareTo(previous)<=0||!stock(r.instrumentId())||r.revision()<0||r.effectiveAfterTradeDate()==null||"EVENT".equals(c.kind())&&!c.instrumentId().equals(r.instrumentId()))throw invalid();
   previous=r.ruleId().toString();refs.put(r.ruleId(),r);
  }
  if(p.nextCursor()!=null&&(p.items().isEmpty()||!p.nextCursor().equals(previous)))throw invalid();
  var seen=new HashSet<UUID>();for(var i:inputs){var r=refs.get(i.ruleId());if(r==null||!seen.add(i.ruleId())||i.revision()!=r.revision()||!i.instrumentId().equals(r.instrumentId())||!i.roundId().equals(c.roundId())||!i.tradeDate().equals(c.expectedDate())||!i.tradeDate().isAfter(r.effectiveAfterTradeDate()))throw invalid();}
 }
 private Cursor locked(String kind){return jdbc.queryForObject("select * from alert_scan_cursor where kind=? for update",(r,n)->new Cursor(r.getString("kind"),r.getLong("completed_event_id"),number(r,"event_id"),r.getString("instrument_id"),r.getString("event_raw_version"),r.getString("round_id"),date(r,"cutoff_date"),date(r,"expected_date"),r.getString("after_id"),r.getLong("revision"),r.getTimestamp("next_at").toInstant(),r.getString("last_reason")),kind);}
 private static Long number(ResultSet r,String field)throws SQLException{long value=r.getLong(field);return r.wasNull()?null:value;}
 private static LocalDate date(ResultSet r,String field)throws SQLException{var d=r.getDate(field);return d==null?null:d.toLocalDate();}
 private static Timestamp ts(Instant now){return Timestamp.from(now);}
 static boolean stock(String s){return s!=null&&s.matches("(SH|SZ)[0-9]{6}");}
 static boolean uuid(String s){return s!=null&&s.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}");}
 private static void checkKind(String k){if(!Set.of("EVENT","RECONCILE").contains(k))throw invalid();}
 static BusinessException invalid(){return new BusinessException("ALERT_SCAN_INVALID","预警扫描请求或进度未通过核验",HttpStatus.BAD_REQUEST);}
 public record Cursor(String kind,long completedEventId,Long eventId,String instrumentId,String eventRawVersion,String roundId,LocalDate cutoffDate,LocalDate expectedDate,String afterId,long revision,Instant nextAt,String lastReason){}
 public record CalendarClaim(UUID operationId,LocalDate start,LocalDate end,UUID owner,Instant leaseUntil){}
}
