package com.aistock.user.position;

import java.util.List;
import java.util.UUID;

/** Internal, session-authorized evidence. Never serialize the session credential. */
public final class PositionSnapshotRecords {
 private PositionSnapshotRecords() {}
 public record SnapshotRequest(String token) {
  @Override public String toString(){return "SnapshotRequest[redacted]";}
 }
 public record PositionItem(UUID positionId,String instrumentId,String quantity,String averageCost,String revision) {}
 public record PositionSnapshot(UUID ownerId,String sourceService,String bookRevision,List<PositionItem> items) {}
 public record PositionRevision(UUID ownerId,String sourceService,String bookRevision) {}
}
