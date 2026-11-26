package com.aistock.gateway.statistics;
import static com.aistock.gateway.statistics.HttpStatisticsRecords.*;import static com.aistock.common.statistics.StatisticsNumbers.*;import java.math.*;import java.time.*;import java.util.*;
/** The monitor is bounded to eight groups and each snapshot is atomic. */
public final class HttpStatisticsCollector {
 private static final List<String> NAMES=List.of("USER","MARKET","PREDICTION","ANALYSIS","COMPARISON","PORTFOLIO","NOTIFICATION","ADMIN");
 private static final class Counter{long requests,error4,error5;BigDecimal millis=BigDecimal.ZERO;}
 private final Instant processStartedAt,observedSince;private final Map<String,Counter> counters=new LinkedHashMap<>();private long dropped;
 public HttpStatisticsCollector(Instant processStartedAt,Clock clock){this.processStartedAt=Objects.requireNonNull(processStartedAt);observedSince=clock.instant();NAMES.forEach(k->counters.put(k,new Counter()));}
 public synchronized void record(String group,int status,long durationNanos){var c=counters.get(group);if(c==null)return;if(durationNanos<0||status<100||status>599){drop();return;}c.requests++;if(status>=400&&status<500)c.error4++;if(status>=500)c.error5++;c.millis=c.millis.add(BigDecimal.valueOf(durationNanos,6));}
 public synchronized void drop(){dropped++;}
 public synchronized HttpSummary snapshot(Instant now){var groups=new ArrayList<Group>();counters.forEach((key,c)->groups.add(new Group(key,count(c.requests),count(c.error4),count(c.error5),c.millis.setScale(3,RoundingMode.HALF_UP).toPlainString(),meanMillis(c.millis,c.requests),ratio(c.error4+c.error5,c.requests))));return new HttpSummary(processStartedAt,observedSince,now,"admin-statistics-v1","CURRENT_GATEWAY_PROCESS",groups,count(dropped));}
}
