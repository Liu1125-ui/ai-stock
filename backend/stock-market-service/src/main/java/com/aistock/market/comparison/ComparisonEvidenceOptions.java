package com.aistock.market.comparison;
import java.time.*;import java.util.*;
import static com.aistock.market.comparison.ComparisonEvidenceRecords.*;
public final class ComparisonEvidenceOptions {
 private ComparisonEvidenceOptions(){}
 public record Stock(String instrumentId,UUID evidenceId,LocalDate sourceFrom,LocalDate sourceTo,Instant capturedAt,LocalDate rawHeadDate,String reason){}
 public record Options(boolean available,String period,LocalDate from,LocalDate to,List<Stock> stocks){}
 public static void validate(List<String> ids,String period){if(ids==null||ids.size()<2||ids.size()>3||new HashSet<>(ids).size()!=ids.size()||ids.stream().anyMatch(id->id==null||!id.matches("(SH|SZ)[0-9]{6}"))||!Set.of("3M","6M","1Y").contains(period==null?"":period))throw ComparisonEvidenceValidator.invalid("COMPARISON_REQUEST_INVALID");}
 public static LocalDate start(LocalDate end,String period){return switch(period){case "3M"->end.minusMonths(3);case "6M"->end.minusMonths(6);case "1Y"->end.minusYears(1);default->throw ComparisonEvidenceValidator.invalid("COMPARISON_REQUEST_INVALID");};}
 public static Options select(List<String> ids,String period,Map<String,List<Evidence>> candidates,Map<String,LocalDate> heads,LocalDate today){
  validate(ids,period);LocalDate end=null;
  for(String id:ids){var latest=candidates.getOrDefault(id,List.of()).stream().map(Evidence::to).max(LocalDate::compareTo).orElse(null);if(latest!=null&&(end==null||latest.isBefore(end)))end=latest;}
  if(end!=null&&end.isAfter(today))throw ComparisonEvidenceValidator.invalid("COMPARISON_REQUEST_INVALID");
  final LocalDate to=end,from=end==null?null:start(end,period);var result=new ArrayList<Stock>();var chosen=new ArrayList<Evidence>();
  for(String id:ids){var list=candidates.getOrDefault(id,List.of());var selected=to==null?null:list.stream().filter(e->"baostock".equals(e.provider())&&"BACKWARD".equals(e.basis())&&!e.from().isAfter(from)&&!e.to().isBefore(to)).findFirst().orElse(null);if(selected!=null)chosen.add(selected);result.add(new Stock(id,selected==null?null:selected.evidenceId(),selected==null?null:selected.from(),selected==null?null:selected.to(),selected==null?null:selected.capturedAt(),heads.get(id),selected==null?(list.isEmpty()?"NO_COMPARISON_EVIDENCE":"INSUFFICIENT_COVERAGE"):null));}
  if(chosen.size()==ids.size()){Map<LocalDate,Boolean> expected=null;boolean conflict=false;for(var e:chosen){var calendar=new TreeMap<LocalDate,Boolean>();for(var day:e.calendar())if(!day.date().isBefore(from)&&!day.date().isAfter(to))calendar.put(day.date(),day.open());if(expected!=null&&!expected.equals(calendar))conflict=true;expected=calendar;}if(conflict)result.replaceAll(s->new Stock(s.instrumentId(),s.evidenceId(),s.sourceFrom(),s.sourceTo(),s.capturedAt(),s.rawHeadDate(),"CALENDAR_CONFLICT"));}
  return new Options(result.stream().allMatch(s->s.reason()==null),period,from,to,List.copyOf(result));
 }
}
