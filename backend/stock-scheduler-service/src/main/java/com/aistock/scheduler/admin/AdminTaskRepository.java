package com.aistock.scheduler.admin;
import com.aistock.common.BusinessException;import java.time.*;import java.sql.*;import java.util.*;import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.jdbc.datasource.DataSourceTransactionManager;import org.springframework.http.HttpStatus;import org.springframework.stereotype.Repository;import org.springframework.transaction.TransactionDefinition;import org.springframework.transaction.support.TransactionTemplate;
import static com.aistock.scheduler.admin.AdminTaskRecords.*;
@Repository public class AdminTaskRepository {
 private final JdbcTemplate jdbc;private final TransactionTemplate read;
 public AdminTaskRepository(JdbcTemplate jdbc){this.jdbc=jdbc;read=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));read.setReadOnly(true);read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);}
 public <T>T read(Supplier<T> work){return read.execute(status->work.get());}
 public List<TaskSummary> list(TaskQuery q,ListCursor cursor){
  var sql=new StringBuilder("select task_id,task_type,instrument_id,target_date,state,created_at,updated_at from scheduled_task t where task_type in ('MARKET_REFRESH','PREDICTION_VERIFY','COMPARISON_UPDATE') and created_at>=? and created_at<?");
  var args=new ArrayList<Object>(List.of(ts(AdminTaskQuery.start(q)),ts(AdminTaskQuery.end(q))));
  if(q.type()!=null){sql.append(" and task_type=?");args.add(q.type());}if(q.state()!=null){sql.append(" and state=?");args.add(q.state());}if(q.instrumentId()!=null){sql.append(" and instrument_id=?");args.add(q.instrumentId());}
  if(cursor!=null){bound(sql,args,cursor.upper(),true);bound(sql,args,cursor.after(),false);}
  sql.append(" order by created_at desc,task_id desc limit ?");args.add(q.limit()+1);
  var rows=jdbc.query(sql.toString(),(r,n)->summary(r,0),args.toArray());
  if(rows.isEmpty())return rows;
  var ids=rows.stream().limit(q.limit()).map(TaskSummary::taskId).toList();
  var totals=new HashMap<String,Long>();jdbc.query("select task_id,count(*) as total from task_attempt where task_id in ("+String.join(",",Collections.nCopies(ids.size(),"?"))+") group by task_id",(org.springframework.jdbc.core.RowCallbackHandler)r->totals.put(r.getString(1),r.getLong(2)),ids.toArray());
  return rows.stream().map(r->new TaskSummary(r.taskId(),r.taskType(),r.instrumentId(),r.targetDate(),r.state(),totals.getOrDefault(r.taskId(),0L),r.createdAt(),r.updatedAt())).toList();
 }
 private void bound(StringBuilder sql,List<Object> args,SortKey key,boolean inclusive){sql.append(" and (t.created_at<? or (t.created_at=? and t.task_id"+(inclusive?"<=":"<")+"?))");args.add(ts(key.createdAt()));args.add(ts(key.createdAt()));args.add(key.taskId());}
 public TaskDetail detail(String id){var rows=jdbc.query("select t.task_id,t.task_type,t.instrument_id,t.target_date,t.state,t.attempt_count,t.next_at,t.created_at,t.updated_at,t.version_id,t.prediction_id,t.rule_version,t.error_code,(select count(*) from task_attempt a where a.task_id=t.task_id) as total from scheduled_task t where t.task_id=? and t.task_type in ('MARKET_REFRESH','PREDICTION_VERIFY','COMPARISON_UPDATE')",(r,n)->new TaskDetail(summary(r,r.getLong("total")),r.getInt("attempt_count"),instant(r,"next_at"),r.getString("version_id"),r.getString("prediction_id"),r.getString("rule_version"),AdminTaskReasons.safe(r.getString("error_code"))),id);if(rows.isEmpty())throw new BusinessException("TASK_NOT_FOUND","任务不存在",HttpStatus.NOT_FOUND);return rows.get(0);}
 public int maxOrdinal(String id){return Objects.requireNonNull(jdbc.queryForObject("select coalesce(max(ordinal),0) from task_attempt where task_id=?",Integer.class,id));}
 public List<Attempt> attempts(String id,int limit,int after,int upper){return jdbc.query("select ordinal,state,started_at,finished_at,version_id,error_code from task_attempt where task_id=? and ordinal>? and ordinal<=? order by ordinal asc limit ?",(r,n)->{
  var start=instant(r,"started_at");var end=instant(r,"finished_at");Long duration=end==null||start==null||end.isBefore(start)?null:Duration.between(start,end).toMillis();var reason=end!=null&&(start==null||end.isBefore(start))?new Reason(null,"执行时间记录不一致，耗时不可用"):AdminTaskReasons.safe(r.getString("error_code"));return new Attempt(r.getInt("ordinal"),r.getString("state"),start,end,duration,r.getString("version_id"),reason);
 },id,after,upper,limit+1);}
 private TaskSummary summary(ResultSet r,long total)throws SQLException{return new TaskSummary(r.getString("task_id"),r.getString("task_type"),r.getString("instrument_id"),r.getDate("target_date").toLocalDate(),r.getString("state"),total,instant(r,"created_at"),instant(r,"updated_at"));}
 private Instant instant(ResultSet r,String column)throws SQLException{var value=r.getTimestamp(column);return value==null?null:value.toInstant();}
 private Timestamp ts(Instant value){return Timestamp.from(value);}
}
