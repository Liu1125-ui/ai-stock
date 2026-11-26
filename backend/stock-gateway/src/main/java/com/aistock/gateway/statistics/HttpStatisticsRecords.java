package com.aistock.gateway.statistics;
import java.time.Instant;import java.util.List;
public final class HttpStatisticsRecords {
 private HttpStatisticsRecords(){}
 public record Group(String group,String completedRequests,String http4xx,String http5xx,String totalDurationMs,String meanDurationMs,String errorPercent){}
 public record HttpSummary(Instant processStartedAt,Instant observedSince,Instant queriedAt,String definitionVersion,String scope,List<Group> groups,String droppedSamples){public HttpSummary{groups=List.copyOf(groups);}}
}
