package com.aistock.scheduler.admin;

import java.time.*;
import java.util.*;

/** Public administrative commands are distinct from internal task attempts. */
public final class AdminTaskActionRecords {
 private AdminTaskActionRecords() {}
 public enum Action { RETRY, CANCEL }
 public record Command(UUID operationId,String expectedRevision,String reasonCode) {}
 public record PolicySnapshot(String taskType,String state,String errorCode,
  boolean activeLease,boolean unresolvedAttempt,boolean workerEnabled,
  Instant retryAvailableAt,LocalDate comparisonCycle,Integer comparisonLimit,
  long totalAttempts,boolean contextValid) {}
 public record Decision(boolean allowed,String reasonCode,String message,Instant retryAvailableAt) {}
 public record Options(String taskId,String revision,Instant queriedAt,boolean actionsEnabled,Decision retry,Decision cancel) {}
 public record Receipt(String operationId,String taskId,String action,String outcome,
  String resultCode,String message,String beforeState,String afterState,
  String beforeRevision,String afterRevision,Instant requestedAt,Instant finishedAt) {}
 public record CommandResult(int status,String code,Receipt receipt) {}
 public record HistoryItem(String actorUserId,String reasonCode,Receipt receipt) {}
 public record HistoryQuery(int limit) {}
 public record HistoryKey(Instant requestedAt,String operationId) {}
 public record HistoryCursor(int version,String taskId,int limit,HistoryKey upper,HistoryKey after) {}
 public record HistoryPage(List<HistoryItem> items,String nextCursor,boolean hasMore,Instant queriedAt) {}
}
