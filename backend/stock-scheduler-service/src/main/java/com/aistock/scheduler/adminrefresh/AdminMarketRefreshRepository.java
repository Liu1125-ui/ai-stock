package com.aistock.scheduler.adminrefresh;

import com.aistock.common.BusinessException;
import com.aistock.common.adminrefresh.*;
import static com.aistock.common.adminrefresh.AdminMarketRefreshRecords.*;
import com.aistock.scheduler.task.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;import java.time.*;import java.time.temporal.ChronoUnit;import java.util.*;import java.security.*;
import org.springframework.beans.factory.annotation.Value;import org.springframework.dao.*;import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;import org.springframework.transaction.*;import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class AdminMarketRefreshRepository {
 private final JdbcTemplate jdbc;private final TransactionTemplate write,read;
 private final TaskRepository tasks;private final AdminMarketRefreshAdmission admission;
 private final AdminMarketRefreshPolicy policy;private final boolean enabled,worker;
 private final AdminMarketRefreshCursor cursors=new AdminMarketRefreshCursor();
 public AdminMarketRefreshRepository(JdbcTemplate jdbc,@Value("${aistock.admin-market-refresh.actions-enabled:false}")boolean enabled,
   @Value("${aistock.scheduler.worker-enabled:false}")boolean worker,@Value("${aistock.admin-market-refresh.cooldown-seconds:60}")long cooldown){
  this.jdbc=jdbc;this.enabled=enabled;this.worker=worker;tasks=new TaskRepository(jdbc);admission=new AdminMarketRefreshAdmission(jdbc,cooldown);policy=new AdminMarketRefreshPolicy(20,1500,cooldown);
  var manager=new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource()));write=new TransactionTemplate(manager);write.setTimeout(5);
  read=new TransactionTemplate(manager);read.setReadOnly(true);read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setTimeout(5);
 }
 public Submission existing(UUID actor,String stock,Command command){validate(actor,stock,command);return existing(actor,stock,command,false);}
 private Submission existing(UUID actor,String stock,Command c,boolean lock){
  var rows=jdbc.queryForList("select * from admin_market_refresh_operation where operation_id=?"+(lock?" for update":""),c.operationId().toString());if(rows.isEmpty())return null;
  var row=rows.get(0);if(!actor.toString().equals(row.get("actor_user_id"))||!hash(actor,stock,c).equals(row.get("request_hash")))throw error("OPERATION_KEY_CONFLICT",409);
  if("PENDING".equals(row.get("outcome")))throw AdminMarketRefreshAdmission.unavailable();return submission(row,true);
 }
 public Submission submit(UUID actor,String stock,Command c,Context context,Instant now){
  validate(actor,stock,c);Objects.requireNonNull(context);Objects.requireNonNull(now);
  try{return write.execute(tx->{
   var guard=admission.lock(stock);var previous=existing(actor,stock,c,true);if(previous!=null)return previous;
   try{jdbc.update("insert into admin_market_refresh_operation(operation_id,actor_user_id,instrument_id,request_hash,reason_code,base_version_id,head_revision,coverage_start,target_date,policy_version,outcome,requested_at)values(?,?,?,?,?,?,?,?,?,?,'PENDING',?)",
    c.operationId().toString(),actor.toString(),stock,hash(actor,stock,c),c.reasonCode(),c.baseVersionId().toString(),AdminMarketRefreshInput.revision(c.expectedHeadRevision()),context.coverageStart(),c.targetDate(),AdminMarketRefreshPolicy.VERSION,ts(now));}
   catch(DuplicateKeyException duplicate){var replay=existing(actor,stock,c,true);if(replay==null)throw AdminMarketRefreshAdmission.unavailable();return replay;}
   String code=null,taskId=null,outcome="REJECTED";
   if(!enabled)code="REFRESH_ACTIONS_DISABLED";
   else if(!worker)code="REFRESH_WORKER_DISABLED";
   else if(!stock.equals(context.instrumentId())||!c.baseVersionId().toString().equals(context.baseVersionId())||!c.expectedHeadRevision().equals(context.headRevision()))code="REFRESH_CONTEXT_CHANGED";
   else{
    var decision=policy.admit(context,c.targetDate(),null,Clock.fixed(now,ZoneOffset.UTC));
    if(!decision.allowed())code=decision.reasonCode();
    else{
     String active=admission.active(guard,true);
     if(active!=null){var contexts=jdbc.queryForList("select * from admin_market_refresh_context where task_id=?",active);
      if(contexts.size()==1&&matches(contexts.get(0),context,c.targetDate())){taskId=active;outcome="ATTACHED";code="REFRESH_TASK_ATTACHED";}
      else code="ACTIVE_REFRESH_EXISTS";
     }else{
      var cooldown=policy.admit(context,c.targetDate(),guard.lastAdmittedAt(),Clock.fixed(now,ZoneOffset.UTC));
      if(!cooldown.allowed())code=cooldown.reasonCode();
      else{var task=tasks.createAdminRefresh(stock,c.targetDate(),c.operationId(),new Constraint(context.baseVersionId(),Long.parseLong(context.headRevision()),context.coverageStart(),AdminMarketRefreshPolicy.VERSION),now);
       taskId=task.taskId();admission.onAdmitted(stock,taskId,now);outcome="QUEUED";code="REFRESH_TASK_QUEUED";}
     }
    }
   }
   jdbc.update("update admin_market_refresh_operation set outcome=?,result_code=?,task_id=?,finished_at=? where operation_id=? and outcome='PENDING'",outcome,code,taskId,ts(now),c.operationId().toString());
   return submission(jdbc.queryForMap("select * from admin_market_refresh_operation where operation_id=?",c.operationId().toString()),false);
  });}catch(DataAccessException|TransactionException e){throw AdminMarketRefreshAdmission.unavailable();}
 }
 public Options options(Context c,Instant now){return read.execute(tx->{
  var range=policy.window(c,Clock.fixed(now,ZoneOffset.UTC));var guard=admission.peek(c.instrumentId());var decision=policy.admit(c,range.defaultTarget(),null,Clock.fixed(now,ZoneOffset.UTC));
  String active=admission.active(guard,false);LocalDate target=range.defaultTarget();String code=decision.reasonCode();boolean allowed=decision.allowed();
  if(allowed&&active!=null){var context=jdbc.queryForList("select * from admin_market_refresh_context where task_id=?",active);if(context.size()==1&&matches(context.get(0),c,date(context.get(0),"target_date"))){target=date(context.get(0),"target_date");allowed=target!=null&&range.min()!=null&&!target.isBefore(range.min())&&!target.isAfter(range.max());code=allowed?null:"ACTIVE_REFRESH_EXISTS";}else{allowed=false;code="ACTIVE_REFRESH_EXISTS";}}
  if(allowed&&active==null){decision=policy.admit(c,target,guard.lastAdmittedAt(),Clock.fixed(now,ZoneOffset.UTC));allowed=decision.allowed();code=decision.reasonCode();}
  if(!enabled){allowed=false;code="REFRESH_ACTIONS_DISABLED";}else if(!worker){allowed=false;code="REFRESH_WORKER_DISABLED";}
  return new Options(now,enabled,allowed,code,c.instrumentId(),"baostock","RAW",c.baseVersionId(),c.headRevision(),c.coverageStart(),c.coverageEnd(),range.min(),range.max(),target,admission.cooldownUntil(guard),active,AdminMarketRefreshPolicy.VERSION);
 });}
 public OperationView receipt(UUID id,Instant now){return read.execute(tx->{var rows=jdbc.queryForList("select * from admin_market_refresh_operation where operation_id=? and outcome<>'PENDING'",id.toString());if(rows.isEmpty())throw error("OPERATION_NOT_FOUND",404);return view(rows.get(0),now);});}
 public HistoryPage history(String stock,HistoryQuery q,Instant now){AdminMarketRefreshInput.instrument(stock);if(q==null||q.size()<1||q.size()>50)throw AdminMarketRefreshInput.invalid();var cursor=q.cursor()==null?null:cursors.decode(q.cursor(),stock,q.size());
  return read.execute(tx->{var sql=new StringBuilder("select * from admin_market_refresh_operation where instrument_id=? and outcome<>'PENDING'");var args=new ArrayList<Object>();args.add(stock);
   if(cursor!=null){bound(sql,args,cursor.upper(),true);bound(sql,args,cursor.after(),false);}sql.append(" order by requested_at desc,operation_id desc limit ?");args.add(q.size()+1);
   var rows=jdbc.queryForList(sql.toString(),args.toArray());boolean more=rows.size()>q.size();var visible=rows.stream().limit(q.size()).toList();String next=null;
   if(more){var upper=cursor==null?key(visible.get(0)):cursor.upper();next=cursors.encode(new AdminMarketRefreshCursor.Cursor(1,stock,q.size(),upper,key(visible.get(visible.size()-1))));}
   return new HistoryPage(now,stock,visible.stream().map(row->view(row,now)).toList(),next,more);
  });
 }
 private OperationView view(Map<String,Object> row,Instant now){var r=receipt(row,false);TaskSnapshot task=null;
  if(r.taskId()!=null){var rows=jdbc.queryForList("select * from scheduled_task where task_id=? and instrument_id=? and task_type='MARKET_REFRESH'",r.taskId(),r.instrumentId());if(rows.size()!=1)throw error("TASK_REFERENCE_ERROR",503);var t=rows.get(0);task=new TaskSnapshot(r.taskId(),text(t,"state"),t.get("revision").toString(),((Number)t.get("attempt_count")).intValue(),text(t,"version_id"),text(t,"error_code"),instant(t,"updated_at"));}
  return new OperationView(now,r,task);
 }
 private Submission submission(Map<String,Object> row,boolean replay){var receipt=receipt(row,replay);boolean rejected="REJECTED".equals(receipt.outcome());return new Submission(rejected?409:200,rejected?receipt.resultCode():"OK",receipt);}
 private Receipt receipt(Map<String,Object> r,boolean replay){return new Receipt(text(r,"operation_id"),text(r,"instrument_id"),text(r,"actor_user_id"),text(r,"outcome"),text(r,"result_code"),text(r,"task_id"),text(r,"base_version_id"),r.get("head_revision").toString(),date(r,"coverage_start"),date(r,"target_date"),text(r,"reason_code"),instant(r,"requested_at"),instant(r,"finished_at"),replay);}
 private boolean matches(Map<String,Object> stored,Context c,LocalDate target){return c.baseVersionId().equals(stored.get("base_version_id"))&&c.headRevision().equals(stored.get("head_revision").toString())&&Objects.equals(c.coverageStart(),date(stored,"coverage_start"))&&Objects.equals(target,date(stored,"target_date"))&&AdminMarketRefreshPolicy.VERSION.equals(stored.get("policy_version"));}
 private void validate(UUID actor,String stock,Command c){AdminMarketRefreshInput.instrument(stock);if(actor==null||c==null||c.operationId()==null||c.baseVersionId()==null||c.targetDate()==null||!AdminMarketRefreshInput.REASONS.contains(Objects.toString(c.reasonCode(),"")))throw AdminMarketRefreshInput.invalid();AdminMarketRefreshInput.revision(c.expectedHeadRevision());}
 private String hash(UUID actor,String stock,Command c){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new ObjectMapper().writeValueAsBytes(List.of(actor.toString(),stock,c.operationId().toString(),c.baseVersionId().toString(),c.expectedHeadRevision(),c.targetDate().toString(),c.reasonCode()))));}catch(Exception e){throw AdminMarketRefreshInput.invalid();}}
 private void bound(StringBuilder sql,List<Object> args,AdminMarketRefreshCursor.Key k,boolean inclusive){sql.append(" and (requested_at<? or(requested_at=? and operation_id"+(inclusive?"<=":"<")+"?))");args.add(ts(k.at()));args.add(ts(k.at()));args.add(k.id());}
 private AdminMarketRefreshCursor.Key key(Map<String,Object> r){return new AdminMarketRefreshCursor.Key(instant(r,"requested_at"),text(r,"operation_id"));}
 private static String text(Map<String,Object> r,String name){return (String)r.get(name);}
 private static LocalDate date(Map<String,Object> r,String name){var v=r.get(name);return v==null?null:((java.sql.Date)v).toLocalDate();}
 private static Instant instant(Map<String,Object> r,String name){var v=r.get(name);return v==null?null:((LocalDateTime)v).toInstant(ZoneOffset.UTC);}
 private static Timestamp ts(Instant now){return Timestamp.from(now.truncatedTo(ChronoUnit.MILLIS));}
 private static BusinessException error(String code,int status){return new BusinessException(code,"操作条件已变化，请核对原记录后再操作",HttpStatus.valueOf(status));}
}