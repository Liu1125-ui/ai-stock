package com.aistock.scheduler.statistics;
import com.aistock.common.*;import com.aistock.common.statistics.*;import static com.aistock.common.statistics.StatisticsRecords.*;import static com.aistock.common.statistics.StatisticsNumbers.*;
import java.time.*;import java.sql.Timestamp;import java.util.*;import org.springframework.http.HttpStatus;import org.springframework.dao.DataAccessException;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.jdbc.datasource.DataSourceTransactionManager;import org.springframework.stereotype.Repository;import org.springframework.transaction.*;import org.springframework.transaction.support.TransactionTemplate;
@Repository public class TaskStatisticsRepository {
 private static final String TYPES="t.task_type in ('MARKET_REFRESH','PREDICTION_VERIFY','COMPARISON_UPDATE')";
 private static final String ATTEMPTS=" from task_attempt a join scheduled_task t on t.task_id=a.task_id where "+TYPES+" and a.started_at>=? and a.started_at<?";
 private final JdbcTemplate jdbc;private final TransactionTemplate read;
 public TaskStatisticsRepository(JdbcTemplate jdbc){this.jdbc=jdbc;read=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));read.setReadOnly(true);read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setTimeout(5);}
 public TaskStatisticsRecords.TasksSummary summary(StatisticsWindow w,Instant now){try{return read.execute(s->aggregate(w,now));}catch(DataAccessException|TransactionException e){throw new BusinessException("STATISTICS_UNAVAILABLE","任务统计暂不可用，请稍后重试",HttpStatus.SERVICE_UNAVAILABLE);}}
 private TaskStatisticsRecords.TasksSummary aggregate(StatisticsWindow w,Instant now){var from=Timestamp.from(w.startInclusive());var to=Timestamp.from(w.endExclusive());
  String tasks=" from scheduled_task t where "+TYPES+" and t.created_at>=? and t.created_at<?";
  var states=groups("select t.state,count(*)"+tasks+" group by t.state",from,to);var types=groups("select t.task_type,count(*)"+tasks+" group by t.task_type",from,to);var attempts=groups("select a.state,count(*)"+ATTEMPTS+" group by a.state",from,to);
  var rows=jdbc.query("select date(date_add(t.created_at,interval 8 hour)) day,count(*) n"+tasks+" group by day",(rs,n)->new DailyCount(rs.getDate("day").toLocalDate(),count(rs.getLong("n"))),from,to);var map=new HashMap<LocalDate,String>();rows.forEach(x->map.put(x.date(),x.count()));var days=new ArrayList<DailyCount>();for(var d=w.from();!d.isAfter(w.to());d=d.plusDays(1))days.add(new DailyCount(d,map.getOrDefault(d,"0")));
  var duration=duration("a.finished_at","a.started_at",ATTEMPTS,from,to);var queue=duration("a.started_at","t.created_at",ATTEMPTS+" and a.ordinal=1",from,to);
  long unfinished=jdbc.queryForObject("select count(*)"+ATTEMPTS+" and a.finished_at is null",Long.class,from,to);
  return new TaskStatisticsRecords.TasksSummary(StatisticsMeta.of(w,now),count(sum(states)),view(states,List.of("QUEUED","RUNNING","RECONCILING","RETRY_WAIT","SUCCEEDED","EMPTY","SKIPPED","FAILED","CANCELLED")),view(types,List.of("MARKET_REFRESH","PREDICTION_VERIFY","COMPARISON_UPDATE")),days,count(sum(attempts)),view(attempts,List.of("RUNNING","UNKNOWN","SUCCEEDED","EMPTY","SKIPPED","FAILED","EXPIRED")),duration,count(unfinished),queue);
 }
 private DurationSummary duration(String end,String start,String source,Timestamp from,Timestamp to){return jdbc.queryForObject("select count(case when "+end+">="+start+" then 1 end) valid,coalesce(sum(case when "+end+">="+start+" then timestampdiff(MICROSECOND,"+start+","+end+")/1000.0 end),0) duration_sum,count(case when "+end+"<"+start+" then 1 end) invalid"+source,(rs,n)->new DurationSummary(count(rs.getLong("valid")),count(rs.getLong("invalid")),meanMillis(rs.getBigDecimal("duration_sum"),rs.getLong("valid"))),from,to);}
 private Map<String,Long> groups(String sql,Timestamp from,Timestamp to){var m=new HashMap<String,Long>();jdbc.query(sql,(org.springframework.jdbc.core.RowCallbackHandler)rs->m.put(rs.getString(1),rs.getLong(2)),from,to);return m;}
 private static long sum(Map<String,Long> m){return m.values().stream().mapToLong(Long::longValue).sum();}
 private static List<CountByKey> view(Map<String,Long> m,List<String> keys){return keys.stream().map(k->new CountByKey(k,count(m.getOrDefault(k,0L)))).toList();}
}
