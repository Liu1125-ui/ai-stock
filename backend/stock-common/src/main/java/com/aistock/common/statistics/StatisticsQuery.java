package com.aistock.common.statistics;
import com.aistock.common.BusinessException;import java.time.*;import java.time.temporal.ChronoUnit;import java.util.*;import org.springframework.http.HttpStatus;import org.springframework.util.MultiValueMap;
public final class StatisticsQuery {
 private StatisticsQuery(){}
 public static StatisticsWindow window(MultiValueMap<String,String> query,Clock clock,Set<String> extras){
  if(query==null||clock==null||extras==null)throw invalid();
  var allowed=new HashSet<>(Set.of("from","to"));allowed.addAll(extras);
  for(var e:query.entrySet())if(!allowed.contains(e.getKey())||e.getValue()==null||e.getValue().size()!=1||e.getValue().get(0)==null)throw invalid();
  var today=clock.instant().atZone(StatisticsWindow.ZONE).toLocalDate();
  if(query.containsKey("from")!=query.containsKey("to"))throw invalid();
  LocalDate from=today.minusDays(6),to=today;
  if(query.containsKey("from")){from=date(query.getFirst("from"));to=date(query.getFirst("to"));}
  if(from.isAfter(to)||to.isAfter(today)||ChronoUnit.DAYS.between(from,to)>=90)throw invalid();
  return new StatisticsWindow(from,to,from.atStartOfDay(StatisticsWindow.ZONE).toInstant(),to.plusDays(1).atStartOfDay(StatisticsWindow.ZONE).toInstant());
 }
 private static LocalDate date(String value){try{if(value==null||!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw invalid();return LocalDate.parse(value);}catch(java.time.DateTimeException e){throw invalid();}}
 public static void none(MultiValueMap<String,String> query){if(query==null||!query.isEmpty())throw invalid();}
 public static int page(MultiValueMap<String,String> query){return integer(query,"page",1,10000);}
 public static int size(MultiValueMap<String,String> query){return integer(query,"size",20,50);}
 private static int integer(MultiValueMap<String,String> query,String key,int fallback,int max){
  if(query==null)throw invalid();if(!query.containsKey(key))return fallback;var values=query.get(key);
  if(values==null||values.size()!=1||values.get(0)==null||!values.get(0).matches("[1-9][0-9]{0,4}"))throw invalid();
  int n=Integer.parseInt(values.get(0));if(n>max)throw invalid();return n;
 }
 public static BusinessException invalid(){return new BusinessException("STATISTICS_QUERY_INVALID","统计查询参数不正确，请检查日期范围",HttpStatus.BAD_REQUEST);}
}
