package com.aistock.scheduler.admin;
import java.time.*;import java.util.List;
public final class AdminTaskRecords {
 private AdminTaskRecords(){}
 public record Reason(String code,String message){}
 public record TaskSummary(String taskId,String taskType,String instrumentId,LocalDate targetDate,String state,long totalAttempts,Instant createdAt,Instant updatedAt){}
 public record TaskDetail(TaskSummary summary,int cycleAttempts,Instant nextAt,String resultVersionId,String predictionId,String ruleVersion,Reason reason){}
 public record Attempt(int ordinal,String state,Instant startedAt,Instant finishedAt,Long durationMillis,String resultVersionId,Reason reason){}
 public record Page<T>(List<T> items,String nextCursor,boolean hasMore,Instant queriedAt){}
 public record Detail(TaskDetail task,Instant queriedAt){}
 public record TaskQuery(String type,String state,String instrumentId,LocalDate createdFrom,LocalDate createdTo,int limit){}
 public record AttemptQuery(int limit){}
 public record SortKey(Instant createdAt,String taskId){}
 public record ListCursor(int version,TaskQuery query,SortKey upper,SortKey after){}
 public record AttemptCursor(int version,String taskId,int limit,int upperOrdinal,int afterOrdinal){}
}
