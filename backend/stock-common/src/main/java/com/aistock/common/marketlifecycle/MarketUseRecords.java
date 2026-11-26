package com.aistock.common.marketlifecycle;

import java.util.*;

/** Wire values and immutable request snapshots; possession of a use ID is not authentication. */
public final class MarketUseRecords {
    private MarketUseRecords() {}
    public enum UseState { ENABLED, DRAINING, DISABLED }
    public record State(UseState useState, long revision, UUID cycleId) {}
    public record Command(String operationId, String action, String expectedRevision, String reason) {}
    public record UseIdentity(String owner, String purpose, String ownerOperationId, String generation) {}
    public record EvidenceBinding(String instrumentId, String evidenceId) {}
    public record Selector(String kind, List<String> versionIds, List<EvidenceBinding> evidence, List<String> instruments) {
        public Selector {
            versionIds=copy(versionIds); evidence=copy(evidence); instruments=copy(instruments);
        }
    }
    private static <T> List<T> copy(List<T> values) {
        return values==null?null:Collections.unmodifiableList(new ArrayList<>(values));
    }
}
