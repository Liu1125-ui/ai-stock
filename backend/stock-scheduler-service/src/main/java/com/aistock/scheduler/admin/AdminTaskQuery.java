package com.aistock.scheduler.admin;
import com.aistock.common.BusinessException;import java.time.*;import java.time.format.*;import java.util.*;
import org.springframework.http.HttpStatus;import org.springframework.util.MultiValueMap;
import static com.aistock.scheduler.admin.AdminTaskRecords.*;
public final class AdminTaskQuery {
 public static final Set<String> TYPES=Set.of("MARKET_REFRESH","PREDICTION_VERIFY","COMPARISON_UPDATE");
 public static final Set<String> STATES=Set.of("QUEUED","RUNNING","RECONCILING","RETRY_WAIT","SUCCEEDED","EMPTY","SKIPPED","FAILED","CANCELLED");
 private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
 private static final Instant LOW=Instant.parse("1000-01-01T00:00:00Z"),HIGH=Instant.parse("9999-12-31T23:59:59.999Z");
 private AdminTaskQuery(){}
 public static TaskQuery parseList(MultiValueMap<String,String> p){
  check(p,Set.of("type","state","instrumentId","createdFrom","createdTo","limit","cursor"));
  try{var q=new TaskQuery(optional(p,"type"),optional(p,"state"),optional(p,"instrumentId"),date(p.getFirst("createdFrom")),date(p.getFirst("createdTo")),limit(p));validate(q);return q;}catch(RuntimeException e){throw invalid();}
 }
 public static void validate(TaskQuery q){
  if(q==null||q.limit()<1||q.limit()>50||q.createdFrom()==null||q.createdTo()==null||q.createdFrom().isAfter(q.createdTo())
   ||q.type()!=null&&!TYPES.contains(q.type())||q.state()!=null&&!STATES.contains(q.state())
   ||q.instrumentId()!=null&&!q.instrumentId().matches("(SH|SZ)[0-9]{6}"))throw invalid();
  try{if(start(q).isBefore(LOW)||end(q).isAfter(HIGH))throw invalid();}catch(DateTimeException|ArithmeticException e){throw invalid();}
 }
 public static AttemptQuery parseAttempts(MultiValueMap<String,String> p){check(p,Set.of("limit","cursor"));try{return new AttemptQuery(limit(p));}catch(RuntimeException e){throw invalid();}}
 public static void noParameters(MultiValueMap<String,String> p){check(p,Set.of());}
 private static void check(MultiValueMap<String,String> p,Set<String> names){if(p==null)throw invalid();for(var e:p.entrySet())if(!names.contains(e.getKey())||e.getValue()==null||e.getValue().size()!=1||e.getValue().get(0)==null||e.getValue().get(0).isEmpty())throw invalid();}
 private static String optional(MultiValueMap<String,String> p,String key){return p.getFirst(key);}
 private static int limit(MultiValueMap<String,String> p){var s=p.getFirst("limit");if(s==null)return 20;if(!s.matches("[0-9]{1,2}"))throw invalid();int n=Integer.parseInt(s);if(n<1||n>50)throw invalid();return n;}
 private static LocalDate date(String s){if(s==null||!s.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw invalid();return LocalDate.parse(s,DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT));}
 public static Instant start(TaskQuery q){return q.createdFrom().atStartOfDay(ZONE).toInstant();}
 public static Instant end(TaskQuery q){return q.createdTo().plusDays(1).atStartOfDay(ZONE).toInstant();}
 public static String validateId(String id){if(id==null||!id.matches("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}"))throw invalid();return id.toLowerCase(Locale.ROOT);}
 public static BusinessException invalid(){return new BusinessException("TASK_QUERY_INVALID","任务查询参数不正确，请检查筛选条件",HttpStatus.BAD_REQUEST);}
}
