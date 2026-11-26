package com.aistock.notification.alertmail;
import java.time.Instant;import java.util.UUID;
public final class AlertMailRecords {
 private AlertMailRecords(){}
 public record Decision(UUID deliveryId,String state,String reasonCode){}
 public record Delivery(UUID deliveryId,UUID triggerId,UUID ownerId,UUID ruleId,long ruleRevision,long preferenceRevision,long accountRevision,String state,int attempts,int checkFailures,Instant createdAt,Instant expiresAt,Instant updatedAt,UUID leaseToken,Instant leaseUntil,Instant sentAt,String reasonCode){}
}
