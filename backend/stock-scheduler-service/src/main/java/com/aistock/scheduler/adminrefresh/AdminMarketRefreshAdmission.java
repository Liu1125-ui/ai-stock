package com.aistock.scheduler.adminrefresh;
import com.aistock.common.BusinessException;import java.sql.Timestamp;import java.time.*;import java.util.*;
import org.springframework.http.HttpStatus;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.transaction.support.TransactionSynchronizationManager;
/** Caller owns the scheduler transaction. Every manual path locks guard before task rows. */
public final class AdminMarketRefreshAdmission {
 private static final Set<String> TERMINAL=Set.of("SUCCEEDED","EMPTY","SKIPPED","FAILED","CANCELLED");
 private final JdbcTemplate jdbc;private final long cooldown;
 public AdminMarketRefreshAdmission(JdbcTemplate jdbc,long cooldown){if(cooldown<1||cooldown>86400)throw new IllegalArgumentException("Invalid stock refresh cooldown");this.jdbc=jdbc;this.cooldown=cooldown;}
 public record GuardSnapshot(String activeTaskId,Instant lastAdmittedAt){}
 public GuardSnapshot lock(String stock){if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("admission requires transaction");jdbc.update("insert into admin_market_refresh_guard(instrument_id)values(?) on duplicate key update instrument_id=instrument_id",stock);return map(jdbc.queryForMap("select * from admin_market_refresh_guard where instrument_id=? for update",stock));}
 public GuardSnapshot peek(String stock){var rows=jdbc.queryForList("select * from admin_market_refresh_guard where instrument_id=?",stock);return rows.isEmpty()?new GuardSnapshot(null,null):map(rows.get(0));}
 public String active(GuardSnapshot guard,boolean lock){if(guard.activeTaskId()==null)return null;var rows=jdbc.queryForList("select state,current_attempt,lease_until from scheduled_task where task_id=?"+(lock?" for update":""),guard.activeTaskId());if(rows.size()!=1)throw unavailable();var t=rows.get(0);long unresolved=jdbc.queryForObject("select count(*) from task_attempt where task_id=? and state in ('RUNNING','UNKNOWN')",Long.class,guard.activeTaskId());return TERMINAL.contains(Objects.toString(t.get("state"),""))&&t.get("current_attempt")==null&&t.get("lease_until")==null&&unresolved==0?null:guard.activeTaskId();}
 public void onAdmitted(String stock,String task,Instant now){jdbc.update("update admin_market_refresh_guard set active_task_id=?,last_admitted_at=? where instrument_id=?",task,Timestamp.from(now),stock);}
 public Instant cooldownUntil(GuardSnapshot g){return g.lastAdmittedAt()==null?null:g.lastAdmittedAt().plusSeconds(cooldown);}
 public String scope(String taskId){var rows=jdbc.queryForList("select instrument_id,idempotency_key from scheduled_task where task_id=?",taskId);if(rows.size()!=1)return null;var t=rows.get(0);return Objects.toString(t.get("idempotency_key"),"").startsWith("admin-market-refresh-")?(String)t.get("instrument_id"):null;}
 public com.aistock.scheduler.admin.AdminTaskActionRecords.Decision retryDecision(String taskId,Instant now,boolean locked){
  String stock=scope(taskId);if(stock==null)return null;
  var rows=jdbc.queryForList("select c.*,t.instrument_id as task_stock,t.target_date as task_target from admin_market_refresh_context c join scheduled_task t on t.task_id=c.task_id where c.task_id=?"+(locked?" for update":""),taskId);
  if(rows.size()!=1)return deny("TASK_ACTION_NOT_ALLOWED",null);
  var c=rows.get(0);
  try {
   var start=((java.sql.Date)c.get("coverage_start")).toLocalDate();var target=((java.sql.Date)c.get("target_date")).toLocalDate();
   new com.aistock.common.adminrefresh.AdminMarketRefreshRecords.Constraint((String)c.get("base_version_id"),((Number)c.get("head_revision")).longValue(),start,(String)c.get("policy_version"));
   if(!stock.equals(c.get("instrument_id"))||!stock.equals(c.get("task_stock"))||!Objects.equals(c.get("target_date"),c.get("task_target"))||target.isBefore(start)||target.isAfter(start.plusDays(1500)))return deny("TASK_ACTION_NOT_ALLOWED",null);
  }catch(RuntimeException invalid){return deny("TASK_ACTION_NOT_ALLOWED",null);}
  var g=locked?lock(stock):peek(stock);String current=active(g,locked);
  if(current!=null&&!current.equals(taskId))return deny("TASK_RESULT_UNCONFIRMED",null);
  Instant until=cooldownUntil(g);if(until!=null&&now.isBefore(until))return deny("TASK_RETRY_COOLDOWN",until);
  return new com.aistock.scheduler.admin.AdminTaskActionRecords.Decision(true,null,null,null);
 }
 public void retryApplied(String taskId,Instant now){String stock=scope(taskId);if(stock!=null)onAdmitted(stock,taskId,now);}
 private com.aistock.scheduler.admin.AdminTaskActionRecords.Decision deny(String code,Instant until){return new com.aistock.scheduler.admin.AdminTaskActionRecords.Decision(false,code,com.aistock.scheduler.admin.AdminTaskActionPolicy.message(code),until);}
 private GuardSnapshot map(Map<String,Object> row){var time=row.get("last_admitted_at");return new GuardSnapshot((String)row.get("active_task_id"),time==null?null:((LocalDateTime)time).toInstant(ZoneOffset.UTC));}
 public static BusinessException unavailable(){return new BusinessException("REFRESH_ACTION_UNAVAILABLE","结果暂无法确认，请查询原操作编号",HttpStatus.SERVICE_UNAVAILABLE);}
}