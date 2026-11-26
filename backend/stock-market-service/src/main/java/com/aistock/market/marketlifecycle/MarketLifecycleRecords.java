package com.aistock.market.marketlifecycle;

import com.aistock.common.marketlifecycle.MarketUseRecords.UseIdentity;
import java.time.Instant;
import java.util.*;

public final class MarketLifecycleRecords {
    private MarketLifecycleRecords() {}
    public record Use(String useId,String versionId,String lifecycleRevision) {}
    public record Bundle(String bundleId,UseIdentity identity,String state,List<String> versionIds,List<Use> uses) {
        public Bundle {versionIds=List.copyOf(versionIds);uses=List.copyOf(uses);}
    }
    public record Grant(Bundle bundle,com.aistock.common.marketlifecycle.MarketUseRecords.Selector selector,Map<String,List<String>> rawByStock) {
        public Grant {var copy=new TreeMap<String,List<String>>();rawByStock.forEach((k,v)->copy.put(k,List.copyOf(v)));rawByStock=Collections.unmodifiableMap(copy);}
    }
    public record Snapshot(String instrumentId,String versionId,String useState,String revision,String cycleId,long activeUses,boolean currentHead) {}
    public record Receipt(String operationId,String instrumentId,String versionId,String action,String expectedRevision,String reason,
                          String state,String errorCode,String revision,String beforeState,String afterState,String cycleId,Instant createdAt,Instant finishedAt) {}
    public record History(List<Receipt> items,String nextCursor) {public History {items=List.copyOf(items);}}
    public record TerminalProof(UseIdentity identity,String state,Instant terminalAt,String fencingRevision,String resultIdentity,List<String> versionIds) {
        public TerminalProof {versionIds=versionIds==null?null:Collections.unmodifiableList(new ArrayList<>(versionIds));}
    }
}
