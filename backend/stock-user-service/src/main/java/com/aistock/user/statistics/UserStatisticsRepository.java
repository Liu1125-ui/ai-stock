package com.aistock.user.statistics;
import com.aistock.common.BusinessException;import com.aistock.common.statistics.*;import static com.aistock.common.statistics.StatisticsNumbers.*;import static com.aistock.common.statistics.StatisticsRecords.*;import static com.aistock.user.statistics.UserStatisticsRecords.*;
import java.time.*;import java.sql.Timestamp;import java.util.*;
import org.springframework.dao.DataAccessException;import org.springframework.http.HttpStatus;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.jdbc.datasource.DataSourceTransactionManager;import org.springframework.stereotype.Repository;import org.springframework.transaction.*;import org.springframework.transaction.support.TransactionTemplate;
@Repository public class UserStatisticsRepository {
 private final JdbcTemplate jdbc;private final TransactionTemplate read;
 public UserStatisticsRepository(JdbcTemplate jdbc){this.jdbc=jdbc;read=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));read.setReadOnly(true);read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setTimeout(5);}
 public UsersSummary summary(StatisticsWindow w,Instant now){try{return read.execute(status->aggregate(w,now));}catch(DataAccessException|TransactionException e){throw new BusinessException("STATISTICS_UNAVAILABLE","用户统计暂不可用，请稍后重试",HttpStatus.SERVICE_UNAVAILABLE);}}
 private UsersSummary aggregate(StatisticsWindow w,Instant now){
  var states=groups("select status,count(*) n from user_account group by status");var roles=groups("select role_code,count(*) n from user_account group by role_code");
  long total=states.values().stream().mapToLong(Long::longValue).sum();var from=Timestamp.from(w.startInclusive());var to=Timestamp.from(w.endExclusive());
  var registrationRows=jdbc.query("select date(date_add(created_at,interval 8 hour)) as day,count(*) n from user_account where created_at>=? and created_at<? group by day",(rs,n)->new DailyCount(rs.getDate("day").toLocalDate(),count(rs.getLong("n"))),from,to);
  var registrationMap=new HashMap<LocalDate,String>();registrationRows.forEach(x->registrationMap.put(x.date(),x.count()));
  var totals=jdbc.queryForMap("select count(*) n,count(distinct user_id) users from login_session where created_at>=? and created_at<?",from,to);
  var loginRows=jdbc.query("select date(date_add(created_at,interval 8 hour)) as day,count(*) n,count(distinct user_id) users from login_session where created_at>=? and created_at<? group by day",(rs,n)->new LoginDay(rs.getDate("day").toLocalDate(),count(rs.getLong("n")),count(rs.getLong("users"))),from,to);
  var loginMap=new HashMap<LocalDate,LoginDay>();loginRows.forEach(x->loginMap.put(x.date(),x));var registrations=new ArrayList<DailyCount>();var logins=new ArrayList<LoginDay>();
  for(var day=w.from();!day.isAfter(w.to());day=day.plusDays(1)){registrations.add(new DailyCount(day,registrationMap.getOrDefault(day,"0")));logins.add(loginMap.getOrDefault(day,new LoginDay(day,"0","0")));}
  long registered=registrationRows.stream().mapToLong(x->Long.parseLong(x.count())).sum();
  return new UsersSummary(StatisticsMeta.of(w,now),count(total),count(states.getOrDefault("ENABLED",0L)),count(states.getOrDefault("DISABLED",0L)),List.of(new CountByKey("USER",count(roles.getOrDefault("USER",0L))),new CountByKey("ADMIN",count(roles.getOrDefault("ADMIN",0L)))),count(registered),registrations,count(((Number)totals.get("n")).longValue()),count(((Number)totals.get("users")).longValue()),logins);
 }
 private Map<String,Long> groups(String sql){var result=new HashMap<String,Long>();jdbc.query(sql,(org.springframework.jdbc.core.RowCallbackHandler)rs->result.put(rs.getString(1),rs.getLong(2)));return result;}
}
