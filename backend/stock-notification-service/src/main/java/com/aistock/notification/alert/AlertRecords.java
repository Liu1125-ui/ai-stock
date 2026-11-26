package com.aistock.notification.alert;
import java.time.*;import java.util.*;
public final class AlertRecords {
 private AlertRecords(){}
 public record Create(UUID requestId,String instrumentId,String condition,String threshold){}
 public record Patch(Long revision,String condition,String threshold,Boolean enabled){}
 public record Boundary(String instrumentId,String provider,UUID rawVersionId,LocalDate latestPublishedTradeDate,LocalDate expectedCompletedTradeDate,LocalDate effectiveAfterTradeDate,String calendarHash,Instant asOf,String definition){}
 public record Rule(UUID id,String instrumentId,String condition,String threshold,boolean enabled,long revision,LocalDate effectiveAfterTradeDate,UUID boundaryRawVersionId,Instant createdAt,Instant updatedAt,String evaluationAvailability){}
 public record Check(String state,Instant checkedAt,LocalDate tradeDate){}
 public record Page(List<Rule> items,int page,int size,long total,Map<UUID,Check> recentChecks){
  public Page(List<Rule> items,int page,int size,long total){this(items,page,size,total,Map.of());}
 }
 public record Preference(boolean emailEnabled,long revision,String deliveryAvailability){}
}
