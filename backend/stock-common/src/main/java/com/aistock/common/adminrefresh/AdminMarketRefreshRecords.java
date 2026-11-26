package com.aistock.common.adminrefresh;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Public command, immutable submission receipt, and independently read task state. */
public final class AdminMarketRefreshRecords {
    private AdminMarketRefreshRecords() {}

    public record Command(UUID operationId, UUID baseVersionId, String expectedHeadRevision,
                          LocalDate targetDate, String reasonCode) {}
    public record Constraint(String baseVersionId,
                             @com.fasterxml.jackson.annotation.JsonProperty(value="headRevision", required=true)
                             @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=com.aistock.common.json.StrictNonNegativeLongDeserializer.class)
                             long headRevision, LocalDate coverageStart, String policyVersion) {
        public Constraint {
            AdminMarketRefreshInput.id(baseVersionId);
            if (headRevision < 0 || coverageStart == null
                || coverageStart.isBefore(LocalDate.of(1990, 1, 1))
                || !AdminMarketRefreshPolicy.VERSION.equals(policyVersion))
                throw AdminMarketRefreshInput.invalid();
        }
    }
    public record Context(String instrumentId, String provider, String priceBasis,
                          boolean eligible, String reasonCode, String baseVersionId,
                          String headRevision, LocalDate coverageStart,
                          LocalDate coverageEnd, Instant queriedAt) {}
    public record Options(Instant queriedAt, boolean actionsEnabled, boolean allowed,
                          String reasonCode, String instrumentId, String provider,
                          String priceBasis, String baseVersionId, String headRevision,
                          LocalDate coverageStart, LocalDate coverageEnd,
                          LocalDate minTargetDate, LocalDate maxTargetDate,
                          LocalDate defaultTargetDate, Instant cooldownUntil,
                          String activeTaskId, String policyVersion) {}
    public record Receipt(String operationId, String instrumentId, String actorUserId,
                          String outcome, String resultCode, String taskId,
                          String baseVersionId, String headRevision, LocalDate coverageStart,
                          LocalDate targetDate, String reasonCode, Instant requestedAt,
                          Instant finishedAt, boolean replayed) {}
    public record TaskSnapshot(String taskId, String state, String revision, int attemptCount,
                               String versionId, String errorCode, Instant updatedAt) {}
    public record OperationView(Instant queriedAt, Receipt receipt, TaskSnapshot task) {}
    public record HistoryPage(Instant queriedAt, String instrumentId,
                              List<OperationView> items, String nextCursor, boolean hasMore) {}
    public record HistoryQuery(String cursor, int size) {}
    public record Submission(int status, String code, Receipt receipt) {}
    public record Decision(boolean allowed, String reasonCode, Instant cooldownUntil) {}
    public record DateWindow(LocalDate min, LocalDate max, LocalDate defaultTarget) {}
}