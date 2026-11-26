package com.aistock.user.position;
import java.time.Instant;import java.util.*;
public final class PositionRecords {
 private PositionRecords(){}
 public record PositionView(String positionId,String instrumentId,String quantity,String averageCost,String revision,Instant createdAt,Instant updatedAt){}
 public record PositionBook(String bookRevision,List<PositionView> items){}
 public record PositionMutation(UUID requestId,String action,UUID positionId,String instrumentId,String quantity,String averageCost,Long expectedRevision){}
 public record PositionReceipt(UUID requestId,String action,String state,int httpStatus,String code,String message,String positionId,String bookRevision,PositionView position){}
 public record StoredReceipt(String hash,PositionReceipt receipt){}
}
