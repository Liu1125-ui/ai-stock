package com.aistock.notification.inbox;
import java.time.*;import java.util.*;
public final class InboxRecords {
 private InboxRecords(){}
 public record Message(UUID id,UUID triggerId,UUID ruleId,String instrumentId,LocalDate tradeDate,String condition,String threshold,String rawClose,String previousBackwardClose,String currentBackwardClose,String definition,UUID evidenceId,UUID rawVersionId,Instant capturedAt,Instant createdAt,Instant readAt){}
 public record Page(List<Message> items,int page,int size,long total,long unreadCount){}
 public record TriggerPage(List<Message> items,int page,int size,long total){}
 public record Unread(long unreadCount){}
 public record ReadResult(List<Message> items){}
}
