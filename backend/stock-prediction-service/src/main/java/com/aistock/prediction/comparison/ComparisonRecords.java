package com.aistock.prediction.comparison;
import java.time.*;import java.util.*;import java.security.*;import com.fasterxml.jackson.databind.*;
public final class ComparisonRecords {
 private ComparisonRecords(){}public static final String FORMULA="comparison-v1";
 public static UUID uuid(String v){if(v==null||!v.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))throw ComparisonErrors.invalid();return UUID.fromString(v);}
 public record Input(UUID requestId,List<String> instrumentIds,LocalDate from,LocalDate to,List<UUID> evidenceIds){
  public Input {instrumentIds=instrumentIds==null?null:List.copyOf(instrumentIds);evidenceIds=evidenceIds==null?null:List.copyOf(evidenceIds);}
  public void validate(Clock clock){if(requestId==null||instrumentIds==null||evidenceIds==null||instrumentIds.size()<2||instrumentIds.size()>3||evidenceIds.size()!=instrumentIds.size()||new HashSet<>(instrumentIds).size()!=instrumentIds.size()||new HashSet<>(evidenceIds).size()!=evidenceIds.size()||instrumentIds.stream().anyMatch(s->!s.matches("(SH|SZ)[0-9]{6}"))||from==null||to==null||to.isAfter(clock.instant().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate())||from.isBefore(LocalDate.of(1990,1,1))||!(from.equals(to.minusMonths(3))||from.equals(to.minusMonths(6))||from.equals(to.minusYears(1))))throw ComparisonErrors.invalid();}
  public String fingerprint(){var pairs=new TreeMap<String,String>();for(int i=0;i<instrumentIds.size();i++)pairs.put(instrumentIds.get(i),evidenceIds.get(i).toString());return hash(List.of(pairs,from.toString(),to.toString(),FORMULA));}
 }
 public record Range(LocalDate from,LocalDate to){}
 public record Metric(String status,String reason,int observationCount,String value,String unit){}
 public record ChartPoint(LocalDate date,String normalizedClose){}
 public record DataIssue(LocalDate date,String field,String state){}
 public record StockResult(String instrumentId,Metric intervalReturn,Metric annualizedVolatility,Metric maxDrawdown,Metric meanVolume,List<ChartPoint> chart,List<DataIssue> dataIssues){}
 public record SourceRef(String instrumentId,UUID evidenceId,UUID rawVersionId,String contentHash,LocalDate from,LocalDate to,Instant capturedAt){}
 public record Calculation(Range requestedRange,Range actualRange,String formulaVersion,String provider,String basis,int annualizationDays,List<StockResult> stocks,List<SourceRef> sourceRefs,String sourceHash){}
 public record Operation(UUID requestId,String state,UUID comparisonId,String errorCode,Instant leaseUntil,boolean expired){}
 public static String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).writeValueAsBytes(value)));}catch(Exception e){throw ComparisonErrors.invalid();}}
}
