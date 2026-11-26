package com.aistock.common.marketrefs;
import java.time.Instant;
import java.util.*;
public final class MarketReferenceRecords {
 private MarketReferenceRecords(){}
 public record Source(String category,String label,String table,List<String> directColumns,List<String> jsonColumns){
  public Source{if(category==null||!category.matches("[a-z][a-z0-9_]{0,63}")||label==null||label.isBlank()||label.length()>80||table==null||!table.matches("[a-z][a-z0-9_]{0,63}")||directColumns==null||jsonColumns==null)throw new IllegalArgumentException("Invalid static reference source");directColumns=List.copyOf(directColumns);jsonColumns=List.copyOf(jsonColumns);var all=new ArrayList<String>(directColumns);all.addAll(jsonColumns);if(all.isEmpty()||all.size()>8||new HashSet<>(all).size()!=all.size()||all.stream().anyMatch(v->v==null||!v.matches("[a-z][a-z0-9_]{0,63}")))throw new IllegalArgumentException("Invalid static reference columns");}
 }
 public record Group(String category,String label,String state,String rows,String reasonCode){}
 public record OwnerResult(String owner,String versionId,Instant queriedAt,String state,String reasonCode,List<Group> groups){public OwnerResult{if(groups!=null)groups=List.copyOf(groups);}}
 public record VersionMeta(String instrumentId,String versionId,String provider,String priceBasis,String versionState,boolean currentHead,Instant queriedAt){}
 public record Report(String instrumentId,String versionId,String provider,String priceBasis,String versionState,boolean currentHead,Instant startedAt,Instant finishedAt,String state,List<OwnerResult> owners){public Report{if(owners!=null)owners=List.copyOf(owners);}}
}
