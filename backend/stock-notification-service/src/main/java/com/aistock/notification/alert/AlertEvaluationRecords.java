package com.aistock.notification.alert;
import java.time.*;import java.util.*;
public final class AlertEvaluationRecords {
 private AlertEvaluationRecords(){}
 public record EvaluateRequest(UUID operationId,UUID ruleId,long revision,UUID evidenceId,Instant leaseUntil){}
 public record EvaluationReceipt(UUID operationId,String state,UUID triggerId,String reasonCode){}
 public record RuleRef(UUID ruleId,UUID ownerId,String instrumentId,long revision,LocalDate effectiveAfterTradeDate){}
 public record RulePage(List<RuleRef> items,String nextCursor){}
 public record Snapshot(UUID ruleId,UUID ownerId,String instrumentId,long revision,String condition,String threshold,LocalDate tradeDate,AlertEvidenceClient.Evidence evidence,Instant createdAt){}
}
