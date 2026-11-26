package com.aistock.scheduler.task;
import java.time.*;import java.util.*;
/** MS04-owned immutable references; no cross-service table access. */
public final class ComparisonTaskRecords {
 private ComparisonTaskRecords(){}
 public record Snapshot(String instrumentId,UUID rawVersionId,String rawContentHash,LocalDate start,LocalDate end,String definition,Instant planAsOf){}
 public record Context(String taskId,LocalDate cycleDate,Snapshot snapshot,int attemptLimit){}
 public record Item(String instrumentId,String state,String reason,UUID rawVersionId,String rawContentHash,
  LocalDate rangeStart,LocalDate targetDate,UUID evidenceId,LocalDate sourceTo){}
 public record Options(Instant asOf,String definition,List<Item> items){
  public Options{items=List.copyOf(items);}
 }
}
