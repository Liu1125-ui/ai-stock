package com.aistock.scheduler.admin;

import com.aistock.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static com.aistock.scheduler.admin.AdminTaskActionRecords.*;

/** All writes stay in one scheduler-database transaction; no business HTTP calls. */
@Repository
public class AdminTaskActionRepository {
 private final JdbcTemplate jdbc;
 private final AdminTaskActionPolicy policy;
 private final TransactionTemplate write,read;
 private final boolean enabled,workerEnabled,comparisonEnabled;
 private final long cooldown;
 private final com.aistock.scheduler.adminrefresh.AdminMarketRefreshAdmission refreshAdmission;
 private final AdminTaskOperationCursorCodec cursors=new AdminTaskOperationCursorCodec();
 public AdminTaskActionRepository(JdbcTemplate jdbc,AdminTaskActionPolicy policy,boolean enabled,long cooldown,boolean workerEnabled,boolean comparisonEnabled){this(jdbc,policy,enabled,cooldown,workerEnabled,comparisonEnabled,60);}
 @org.springframework.beans.factory.annotation.Autowired
 public AdminTaskActionRepository(JdbcTemplate jdbc,AdminTaskActionPolicy policy,
  @Value("${aistock.admin-task.actions-enabled:false}")boolean enabled,
  @Value("${aistock.admin-task.retry-cooldown-seconds:60}")long cooldown,
  @Value("${aistock.scheduler.worker-enabled:false}")boolean workerEnabled,
  @Value("${aistock.comparison-update.worker-enabled:false}")boolean comparisonEnabled,
  @Value("${aistock.admin-market-refresh.cooldown-seconds:60}")long marketRefreshCooldown) {
  if(cooldown<1)throw new IllegalArgumentException("Invalid task retry cooldown");
  this.jdbc=jdbc;this.policy=policy;this.enabled=enabled;this.cooldown=cooldown;this.workerEnabled=workerEnabled;this.comparisonEnabled=comparisonEnabled;
  this.refreshAdmission=new com.aistock.scheduler.adminrefresh.AdminMarketRefreshAdmission(jdbc,marketRefreshCooldown);
  var manager=new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource()));
  write=new TransactionTemplate(manager);write.setTimeout(5);
  read=new TransactionTemplate(manager);read.setReadOnly(true);read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
 }
 public Options options(UUID id,Instant now) {
  require(id);requireTime(now);
  return read.execute(status->{var task=task(id,false);var snapshot=snapshot(task);
   return new Options(id.toString(),version(task),now,enabled,
    retryDecision(id,policy.evaluate(Action.RETRY,snapshot,now,enabled),now,false),policy.evaluate(Action.CANCEL,snapshot,now,enabled));});
 }
 public CommandResult execute(UUID actor,UUID id,Action action,Command input,Instant now) {
  require(actor);require(id);requireTime(now);
  if(input==null||input.operationId()==null||action==null)throw AdminTaskActionInput.invalid();
  long expected=AdminTaskActionInput.revision(input.expectedRevision());
  var allowedReasons=action==Action.RETRY?Set.of("RETRY_TRANSIENT_FAILURE","RETRY_DEPENDENCY_RECOVERED"):Set.of("CANCEL_NOT_NEEDED","CANCEL_DUPLICATE_REQUEST");
  if(input.reasonCode()==null||!allowedReasons.contains(input.reasonCode()))throw AdminTaskActionInput.invalid();
  if(!enabled)throw error("TASK_ACTIONS_DISABLED",HttpStatus.CONFLICT);
  var time=now.truncatedTo(ChronoUnit.MILLIS);var hash=hash(actor,id,action,input);
  try {
   return write.execute(status->{
    if(action==Action.RETRY){String stock=refreshAdmission.scope(id.toString());if(stock!=null){var guard=refreshAdmission.lock(stock);var ids=new java.util.TreeSet<String>();ids.add(id.toString());if(guard.activeTaskId()!=null)ids.add(guard.activeTaskId());for(String taskId:ids)jdbc.queryForList("select task_id from scheduled_task where task_id=? for update",taskId);}}
    try {
     jdbc.update("insert into admin_task_operation(operation_id,task_id,actor_user_id,action,request_hash,expected_revision,reason_code,outcome,requested_at) values(?,?,?,?,?,?,?,'PENDING',?)",
      input.operationId().toString(),id.toString(),actor.toString(),action.name(),hash,expected,input.reasonCode(),ts(time));
    }catch(DuplicateKeyException duplicate) {
     var previous=operation(input.operationId(),true);
     if(!actor.toString().equals(previous.get("actor_user_id"))||!hash.equals(previous.get("request_hash")))throw error("OPERATION_KEY_CONFLICT",HttpStatus.CONFLICT);
     if(!Set.of("APPLIED","REJECTED").contains(text(previous,"outcome")))throw error("TASK_ACTION_UNAVAILABLE",HttpStatus.SERVICE_UNAVAILABLE);
     return result(previous);
    }
    var current=task(id,true);long before=number(current,"revision");
    String beforeState=text(current,"state");
    Decision decision=before==expected?policy.evaluate(action,snapshot(current),now,true):
     new Decision(false,"TASK_REVISION_CONFLICT",AdminTaskActionPolicy.message("TASK_REVISION_CONFLICT"),null);
    if(action==Action.RETRY)decision=retryDecision(id,decision,now,true);
    String code=decision.reasonCode();String outcome="REJECTED";String afterState=beforeState;long after=before;
    if(decision.allowed()) {
     int affected;
     if(action==Action.CANCEL) {
      affected=jdbc.update("update scheduled_task set state='CANCELLED',updated_at=?,revision=revision+1 where task_id=? and revision=? and state in ('QUEUED','RETRY_WAIT')",ts(time),id.toString(),expected);
      afterState="CANCELLED";code="TASK_CANCEL_APPLIED";
     }else {
      var reset="COMPARISON_UPDATE".equals(current.get("task_type"))?"":"attempt_count=0,";
      affected=jdbc.update("update scheduled_task set state='QUEUED',"+reset+"error_code=NULL,version_id=NULL,next_at=?,updated_at=?,revision=revision+1 where task_id=? and revision=? and state='FAILED'",ts(time),ts(time),id.toString(),expected);
      afterState="QUEUED";code="TASK_RETRY_APPLIED";
     }
     if(affected!=1)throw error("TASK_ACTION_UNAVAILABLE",HttpStatus.SERVICE_UNAVAILABLE);
     if(action==Action.RETRY)refreshAdmission.retryApplied(id.toString(),time);
     after=before+1;outcome="APPLIED";
    }
    int saved=jdbc.update("update admin_task_operation set outcome=?,result_code=?,before_state=?,after_state=?,before_revision=?,after_revision=?,finished_at=? where operation_id=? and outcome='PENDING'",
     outcome,code,beforeState,afterState,before,after,ts(time),input.operationId().toString());
    if(saved!=1)throw error("TASK_ACTION_UNAVAILABLE",HttpStatus.SERVICE_UNAVAILABLE);
    return result(operation(input.operationId(),false));
   });
  }catch(DataAccessException|org.springframework.transaction.TransactionException e){throw error("TASK_ACTION_UNAVAILABLE",HttpStatus.SERVICE_UNAVAILABLE);}
 }
 public Receipt receipt(UUID actor,UUID id,UUID operation) {
  require(actor);require(id);require(operation);
  var rows=jdbc.queryForList("select * from admin_task_operation where operation_id=? and task_id=? and actor_user_id=? and outcome in ('APPLIED','REJECTED')",operation.toString(),id.toString(),actor.toString());
  if(rows.isEmpty())throw error("OPERATION_NOT_FOUND",HttpStatus.NOT_FOUND);
  return receipt(rows.get(0));
 }
 public HistoryPage history(UUID id,HistoryQuery query,String cursor,Instant now) {
  require(id);requireTime(now);if(query==null||query.limit()<1||query.limit()>50)throw AdminTaskActionInput.invalid();
  var c=cursor==null?null:cursors.decode(cursor,id.toString(),query.limit());
  return read.execute(status->{task(id,false);
   var sql=new StringBuilder("select * from admin_task_operation where task_id=? and outcome in ('APPLIED','REJECTED')");
   var args=new ArrayList<Object>();args.add(id.toString());
   if(c!=null){bound(sql,args,c.upper(),true);bound(sql,args,c.after(),false);}
   sql.append(" order by requested_at desc,operation_id desc limit ?");args.add(query.limit()+1);
   var rows=jdbc.queryForList(sql.toString(),args.toArray());boolean more=rows.size()>query.limit();
   var items=rows.stream().limit(query.limit()).map(r->new HistoryItem(text(r,"actor_user_id"),text(r,"reason_code"),receipt(r))).toList();
   String next=null;if(more){var upper=c==null?key(rows.get(0)):c.upper();next=cursors.encode(new HistoryCursor(1,id.toString(),query.limit(),upper,key(rows.get(query.limit()-1))));}
   return new HistoryPage(items,next,more,now);
  });
 }
 private Decision retryDecision(UUID id,Decision original,Instant now,boolean locked){if(!original.allowed())return original;var extra=refreshAdmission.retryDecision(id.toString(),now,locked);return extra==null?original:extra;}
 private PolicySnapshot snapshot(Map<String,Object> task) {
  var id=text(task,"task_id");String type=text(task,"task_type");
  long total=jdbc.queryForObject("select count(*) from task_attempt where task_id=?",Long.class,id);
  boolean unresolved=jdbc.queryForObject("select count(*) from task_attempt where task_id=? and state in ('RUNNING','UNKNOWN')",Long.class,id)>0;
  var last=jdbc.queryForList("select max(finished_at) as last_at from admin_task_operation where task_id=? and action='RETRY' and outcome='APPLIED'",id).get(0).get("last_at");
  Instant available=last==null?null:instant(last).plusSeconds(cooldown);
  LocalDate cycle=null;Integer limit=null;boolean valid=true;
  if("COMPARISON_UPDATE".equals(type)) {
   var contexts=jdbc.queryForList("select * from comparison_update_context where task_id=?",id);
   valid=contexts.size()==1;
   if(valid) {
    var c=contexts.get(0);
    try {
     cycle=date(c.get("cycle_date"));limit=((Number)c.get("attempt_limit")).intValue();
     var start=date(c.get("range_start"));var end=date(c.get("target_date"));
     AdminTaskActionInput.validateId(text(c,"raw_version_id"));
     valid=Objects.equals(c.get("instrument_id"),task.get("instrument_id"))&&end.equals(date(task.get("target_date")))
      &&start.equals(end.minusYears(1))&&text(c,"raw_content_hash")!=null&&text(c,"raw_content_hash").matches("[a-f0-9]{64}")
      &&"baostock-comparison-backward-close-v1".equals(c.get("definition"))
      &&cycle.equals(instant(c.get("plan_as_of")).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate());
    }catch(RuntimeException e){valid=false;}
   }
  }
  return new PolicySnapshot(type,text(task,"state"),text(task,"error_code"),task.get("current_attempt")!=null||task.get("lease_until")!=null,
   unresolved,"COMPARISON_UPDATE".equals(type)?comparisonEnabled:workerEnabled,available,cycle,limit,total,valid);
 }
 private Map<String,Object> task(UUID id,boolean lock) {
  var rows=jdbc.queryForList("select * from scheduled_task where task_id=?"+(lock?" for update":""),id.toString());
  if(rows.isEmpty())throw error("TASK_NOT_FOUND",HttpStatus.NOT_FOUND);return rows.get(0);
 }
 private Map<String,Object> operation(UUID id,boolean lock) {
  var rows=jdbc.queryForList("select * from admin_task_operation where operation_id=?"+(lock?" for update":""),id.toString());
  if(rows.isEmpty())throw error("OPERATION_NOT_FOUND",HttpStatus.NOT_FOUND);return rows.get(0);
 }
 private CommandResult result(Map<String,Object> row) {
  var receipt=receipt(row);boolean applied="APPLIED".equals(receipt.outcome());return new CommandResult(applied?200:409,applied?"OK":receipt.resultCode(),receipt);
 }
 private Receipt receipt(Map<String,Object> r) {
  String code=text(r,"result_code");return new Receipt(text(r,"operation_id"),text(r,"task_id"),text(r,"action"),text(r,"outcome"),code,
   AdminTaskActionPolicy.message(code),text(r,"before_state"),text(r,"after_state"),version(r,"before_revision"),version(r,"after_revision"),instant(r.get("requested_at")),instant(r.get("finished_at")));
 }
 private void bound(StringBuilder sql,List<Object> args,HistoryKey k,boolean inclusive) {
  sql.append(" and (requested_at<? or (requested_at=? and operation_id"+(inclusive?"<=":"<")+"?))");args.add(ts(k.requestedAt()));args.add(ts(k.requestedAt()));args.add(k.operationId());
 }
 private HistoryKey key(Map<String,Object> r){return new HistoryKey(instant(r.get("requested_at")),text(r,"operation_id"));}
 private String hash(UUID actor,UUID task,Action action,Command input) {
  try {var bytes=new ObjectMapper().writeValueAsBytes(List.of(actor.toString(),task.toString(),action.name(),input.operationId().toString(),input.expectedRevision(),input.reasonCode()));return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
  catch(Exception e){throw AdminTaskActionInput.invalid();}
 }
 private String text(Map<String,Object> r,String key){var v=r.get(key);return v==null?null:v.toString();}
 private long number(Map<String,Object> r,String key){return ((Number)r.get(key)).longValue();}
 private String version(Map<String,Object> r){return version(r,"revision");}
 private String version(Map<String,Object> r,String key){var v=r.get(key);return v==null?null:Long.toString(((Number)v).longValue());}
 private Instant instant(Object v){if(v==null)return null;if(v instanceof Timestamp t)return t.toInstant();if(v instanceof LocalDateTime t)return t.toInstant(ZoneOffset.UTC);throw new IllegalArgumentException("Invalid stored time");}
 private LocalDate date(Object v){if(v instanceof java.sql.Date d)return d.toLocalDate();if(v instanceof LocalDate d)return d;throw new IllegalArgumentException("Invalid stored date");}
 private Timestamp ts(Instant t){return Timestamp.from(t.truncatedTo(ChronoUnit.MILLIS));}
 private void require(UUID id){if(id==null)throw AdminTaskActionInput.invalid();}
 private void requireTime(Instant t){if(t==null||t.isBefore(Instant.parse("1000-01-01T00:00:00Z"))||t.isAfter(Instant.parse("9999-12-30T00:00:00Z")))throw AdminTaskActionInput.invalid();}
 private BusinessException error(String code,HttpStatus status){return new BusinessException(code,AdminTaskActionPolicy.message(code),status);}
}
