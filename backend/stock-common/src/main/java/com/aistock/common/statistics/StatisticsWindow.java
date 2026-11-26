package com.aistock.common.statistics;
import java.time.*;import org.springframework.util.MultiValueMap;
/** A Shanghai calendar window with UTC half-open query bounds. */
public record StatisticsWindow(LocalDate from,LocalDate to,Instant startInclusive,Instant endExclusive) {
 public static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
 public static StatisticsWindow parse(MultiValueMap<String,String> query,Clock clock){return StatisticsQuery.window(query,clock,java.util.Set.of());}
}
