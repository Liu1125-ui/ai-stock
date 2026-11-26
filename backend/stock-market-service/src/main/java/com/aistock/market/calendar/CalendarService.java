package com.aistock.market.calendar;
import static com.aistock.market.calendar.CalendarRecords.*;
import com.aistock.common.BusinessException;
import com.fasterxml.jackson.databind.*;
import java.time.*;import java.util.*;import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service public class CalendarService {
 private final JdbcTemplate jdbc;private final CalendarCollector collector;private final Clock clock;private final int ttlDays;private final TransactionTemplate tx;
 private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
 public CalendarService(JdbcTemplate jdbc,CalendarCollector collector,Clock clock,@Value("${aistock.market.calendar-ttl-days:7}")int ttlDays){if(ttlDays<1||ttlDays>30)throw new IllegalArgumentException("Invalid calendar TTL");this.jdbc=jdbc;this.collector=collector;this.clock=clock;this.ttlDays=ttlDays;tx=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));tx.setTimeout(5);}
 public Snapshot refresh(Request request){
  if(request==null||request.operationId()==null||!request.operationId().matches("[A-Za-z0-9_-]{1,128}")||request.start()==null||request.end()==null)throw invalid();
  var previous=replay(request);if(previous!=null)return previous;
  var today=LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
  if(request.start().isBefore(today.minusDays(366))||request.end().isAfter(today.plusDays(90))||request.end().isBefore(request.start())||java.time.temporal.ChronoUnit.DAYS.between(request.start(),request.end())>=400)throw invalid();
  Candidate c;try{c=collector.collect(request);}catch(Exception unavailable){throw unavailable();}
  validate(request,c);
  return tx.execute(status->{
   jdbc.update("insert into independent_calendar_head(provider,version_id) values('baostock',null) on duplicate key update provider=values(provider)");
   jdbc.queryForMap("select provider from independent_calendar_head where provider='baostock' for update");
   var saved=replay(request);if(saved!=null)return saved;
   var id=UUID.randomUUID();jdbc.update("insert into independent_calendar_version(version_id,provider,range_start,range_end,fetched_at,source_hash,candidate_json) values(?,?,?,?,?,?,?)",id.toString(),c.provider(),c.start(),c.end(),Timestamp.from(c.fetchedAt()),c.sourcePayloadHash(),json(c));
   jdbc.update("insert into independent_calendar_operation(operation_id,range_start,range_end,version_id) values(?,?,?,?)",request.operationId(),request.start(),request.end(),id.toString());
   var heads=jdbc.queryForList("select v.fetched_at,v.range_end from independent_calendar_head h join independent_calendar_version v on h.version_id=v.version_id where h.provider='baostock'");
   boolean newer=heads.isEmpty()||(!c.fetchedAt().isBefore(((Timestamp)heads.get(0).get("fetched_at")).toInstant())&&!c.end().isBefore(((java.sql.Date)heads.get(0).get("range_end")).toLocalDate()));
   if(newer)jdbc.update("update independent_calendar_head set version_id=? where provider='baostock'",id.toString());
   return new Snapshot(id,c);
  });
 }
 public Snapshot current(){var rows=jdbc.queryForList("select v.version_id,v.candidate_json from independent_calendar_head h join independent_calendar_version v on v.version_id=h.version_id where h.provider='baostock'");if(rows.isEmpty())throw unavailable();return decode(rows.get(0));}
 public Optional<Snapshot> covering(LocalDate rawLast,LocalDate cutoff){
  var rows=jdbc.queryForList("select version_id,candidate_json from independent_calendar_version where provider='baostock' and range_start<=? and range_end>=? and fetched_at>=? and fetched_at<=? order by fetched_at desc,version_id desc limit 1",rawLast.isBefore(cutoff)?rawLast:cutoff,rawLast.isAfter(cutoff)?rawLast:cutoff,Timestamp.from(clock.instant().minus(Duration.ofDays(ttlDays))),Timestamp.from(clock.instant().plusSeconds(5)));
  if(rows.isEmpty())return Optional.empty();var snapshot=decode(rows.get(0));var c=snapshot.candidate();
  if(c.fetchedAt().isBefore(clock.instant().minus(Duration.ofDays(ttlDays)))||c.fetchedAt().isAfter(clock.instant().plusSeconds(5))||c.start().isAfter(rawLast)||c.start().isAfter(cutoff)||c.end().isBefore(rawLast)||c.end().isBefore(cutoff))return Optional.empty();return Optional.of(snapshot);
 }
 private Snapshot replay(Request r){var rows=jdbc.queryForList("select o.range_start,o.range_end,v.version_id,v.candidate_json from independent_calendar_operation o join independent_calendar_version v on v.version_id=o.version_id where o.operation_id=?",r.operationId());if(rows.isEmpty())return null;var row=rows.get(0);if(!r.start().equals(((java.sql.Date)row.get("range_start")).toLocalDate())||!r.end().equals(((java.sql.Date)row.get("range_end")).toLocalDate()))throw new BusinessException("CALENDAR_OPERATION_CONFLICT","日历请求标识已用于不同范围",HttpStatus.CONFLICT);return decode(row);}
 private Snapshot decode(Map<String,Object> row){try{return new Snapshot(UUID.fromString((String)row.get("version_id")),mapper.readValue((String)row.get("candidate_json"),Candidate.class));}catch(Exception error){throw unavailable();}}
 private void validate(Request r,Candidate c){
  if(c==null||!"baostock".equals(c.provider())||!r.start().equals(c.start())||!r.end().equals(c.end())||c.fetchedAt()==null||c.fetchedAt().isBefore(clock.instant().minusSeconds(300))||c.fetchedAt().isAfter(clock.instant().plusSeconds(5))||c.days()==null||c.days().size()!=java.time.temporal.ChronoUnit.DAYS.between(r.start(),r.end())+1)throw invalid();
  var expected=r.start();for(var day:c.days()){if(day==null||day.open()==null||!expected.equals(day.date()))throw invalid();expected=expected.plusDays(1);}
  if(!hash(c.days()).equals(c.sourcePayloadHash()))throw invalid();
 }
 public static String hash(List<Day> days){try{var text=new StringBuilder();for(var day:days)text.append(day.date()).append(':').append(Boolean.TRUE.equals(day.open())?'1':'0').append('\n');return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8)));}catch(Exception error){throw invalid();}}
 private String json(Candidate c){try{return mapper.writeValueAsString(c);}catch(Exception error){throw invalid();}}
 private static BusinessException invalid(){return new BusinessException("CALENDAR_SOURCE_INVALID","交易日历范围或来源完整性未通过核验，旧版本保留",HttpStatus.UNPROCESSABLE_ENTITY);}
 private static BusinessException unavailable(){return new BusinessException("CALENDAR_SOURCE_UNAVAILABLE","交易日历暂不可用，旧版本保留",HttpStatus.SERVICE_UNAVAILABLE);}
}
