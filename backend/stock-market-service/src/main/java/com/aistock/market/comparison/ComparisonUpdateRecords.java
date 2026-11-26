package com.aistock.market.comparison;
import java.time.*;import java.util.*;
public final class ComparisonUpdateRecords {
 private ComparisonUpdateRecords(){}
 public record Options(Instant asOf,String definition,List<Item> items){
  public Options { items=List.copyOf(items); }
 }
 public record Item(String instrumentId,String state,String reason,UUID rawVersionId,
  String rawContentHash,LocalDate rangeStart,LocalDate targetDate,UUID evidenceId,LocalDate sourceTo){}
 public record Request(UUID operationId,String instrumentId,UUID rawVersionId,String rawContentHash,
  LocalDate start,LocalDate end,String definition,Instant leaseUntil){}
 public record Result(String operationId,String state,String versionId,String errorCode){}
}
