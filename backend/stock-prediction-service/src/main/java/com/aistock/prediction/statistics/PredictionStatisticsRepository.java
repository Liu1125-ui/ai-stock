package com.aistock.prediction.statistics;
import com.aistock.common.*;import com.aistock.common.statistics.*;import static com.aistock.common.statistics.StatisticsRecords.*;import static com.aistock.common.statistics.StatisticsNumbers.*;import static com.aistock.prediction.statistics.PredictionStatisticsRecords.*;
import java.time.*;import java.sql.Timestamp;import java.util.*;import org.springframework.http.HttpStatus;import org.springframework.dao.DataAccessException;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.jdbc.datasource.DataSourceTransactionManager;import org.springframework.stereotype.Repository;import org.springframework.transaction.*;import org.springframework.transaction.support.TransactionTemplate;
@Repository public class PredictionStatisticsRepository {
 private final JdbcTemplate jdbc;private final TransactionTemplate read;
 public PredictionStatisticsRepository(JdbcTemplate jdbc){this.jdbc=jdbc;read=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));read.setReadOnly(true);read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setTimeout(5);}
 public PredictionsSummary summary(StatisticsWindow w,Instant now){try{return read.execute(s->aggregate(w,now));}catch(DataAccessException|TransactionException e){throw new BusinessException("STATISTICS_UNAVAILABLE","预测统计暂不可用，请稍后重试",HttpStatus.SERVICE_UNAVAILABLE);}}
 private PredictionsSummary aggregate(StatisticsWindow w,Instant now){
  var from=Timestamp.from(w.startInclusive());var to=Timestamp.from(w.endExclusive());var states=new HashMap<String,Long>();
  jdbc.query("select state,count(*) from forecast_request where created_at>=? and created_at<? group by state",(org.springframework.jdbc.core.RowCallbackHandler)rs->states.put(rs.getString(1),rs.getLong(2)),from,to);
  long total=states.values().stream().mapToLong(Long::longValue).sum();var groups=List.of("RUNNING","SUCCEEDED","FAILED").stream().map(k->new CountByKey(k,count(states.getOrDefault(k,0L)))).toList();
  var dayRows=jdbc.query("select date(date_add(created_at,interval 8 hour)) as day,count(*) n,sum(state='SUCCEEDED') succeeded,sum(state='FAILED') failed,sum(state='RUNNING') running from forecast_request where created_at>=? and created_at<? group by day",(rs,n)->new PredictionDay(rs.getDate("day").toLocalDate(),count(rs.getLong("n")),count(rs.getLong("succeeded")),count(rs.getLong("failed")),count(rs.getLong("running"))),from,to);
  var map=new HashMap<LocalDate,PredictionDay>();dayRows.forEach(x->map.put(x.date(),x));var days=new ArrayList<PredictionDay>();for(var d=w.from();!d.isAfter(w.to());d=d.plusDays(1))days.add(map.getOrDefault(d,new PredictionDay(d,"0","0","0","0")));
  var models=jdbc.query("select f.model_id,m.artifact_id,count(*) n,sum(f.state='SUCCEEDED') succeeded from forecast_request f left join model_version m on m.model_id=f.model_id where f.created_at>=? and f.created_at<? group by f.model_id,m.artifact_id order by n desc,f.model_id asc",(rs,n)->{var artifact=rs.getString("artifact_id");long used=rs.getLong("n");return new ModelUsage(rs.getString("model_id"),artifact,artifact==null?"历史版本信息暂缺":"Transformer版本 · "+artifact,count(used),count(rs.getLong("succeeded")),ratio(used,total));},from,to);
  return new PredictionsSummary(StatisticsMeta.of(w,now),count(total),groups,days,models);
 }
}
