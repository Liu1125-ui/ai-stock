package com.aistock.market.comparison;
import com.aistock.market.quotation.*;
import java.time.*;import java.math.BigDecimal;import java.util.*;
/** Pure policy over supplier calendars; never infer trading days from weekdays. */
public final class ComparisonUpdatePolicy {
 private ComparisonUpdatePolicy(){}
 public static LocalDate target(List<TradingDay> days,Instant now,int hour){
  if(now==null||hour<0||hour>23||days==null||days.isEmpty())throw invalid();
  var local=now.atZone(ZoneId.of("Asia/Shanghai"));
  var cutoff=local.getHour()<hour?local.toLocalDate().minusDays(1):local.toLocalDate();
  var first=days.get(0);
  if(first==null||first.date()==null)throw invalid();
  var map=calendar(days,first.date(),days.get(days.size()-1).date());
  if(!map.containsKey(cutoff))throw invalid();
  return map.entrySet().stream().filter(e->!e.getKey().isAfter(cutoff)&&e.getValue())
   .map(Map.Entry::getKey).max(LocalDate::compareTo).orElseThrow(ComparisonUpdatePolicy::invalid);
 }
 public static boolean sameWindow(List<RawBar> a,List<TradingDay> ca,List<RawBar> b,
   List<TradingDay> cb,LocalDate from,LocalDate to){
  return projection(a,from,to).equals(projection(b,from,to))&&calendar(ca,from,to).equals(calendar(cb,from,to));
 }
 static NavigableMap<LocalDate,Boolean> calendar(List<TradingDay> days,LocalDate from,LocalDate to){
  if(days==null||from==null||to==null||from.isAfter(to))throw invalid();
  var map=new TreeMap<LocalDate,Boolean>();
  LocalDate previous=null;
  for(var d:days){
   if(d==null||d.date()==null||previous!=null&&!d.date().isAfter(previous))throw invalid();
   previous=d.date();
   if(!d.date().isBefore(from)&&!d.date().isAfter(to))map.put(d.date(),d.open());
  }
  var expected=from;
  for(var date:map.keySet()){if(!date.equals(expected))throw invalid();expected=expected.plusDays(1);}
  if(!expected.equals(to.plusDays(1)))throw invalid();
  return map;
 }
 private static Map<LocalDate,List<String>> projection(List<RawBar> bars,LocalDate from,LocalDate to){
  if(bars==null)throw ComparisonEvidenceValidator.invalid("COMPARISON_RAW_INVALID");
  var out=new TreeMap<LocalDate,List<String>>();
  for(var b:bars){
   if(b==null||b.date()==null)throw ComparisonEvidenceValidator.invalid("COMPARISON_RAW_INVALID");
   if(b.date().isBefore(from)||b.date().isAfter(to))continue;
   var row=Arrays.asList(canonical(b.close()),canonical(b.volumeShares()),Integer.toString(b.tradeStatus()));
   if(out.put(b.date(),row)!=null)throw ComparisonEvidenceValidator.invalid("COMPARISON_RAW_INVALID");
  }
  return out;
 }
 private static String canonical(BigDecimal n){return n==null?null:n.stripTrailingZeros().toPlainString();}
 private static com.aistock.common.BusinessException invalid(){
  return ComparisonEvidenceValidator.invalid("COMPARISON_CALENDAR_UNAVAILABLE");
 }
}
