package com.aistock.common.statistics;
import java.time.LocalDate;
public final class StatisticsRecords {
 private StatisticsRecords(){}
 public record CountByKey(String key,String count){}
 public record DailyCount(LocalDate date,String count){}
 public record DurationSummary(String sampleCount,String excludedInvalidCount,String meanMs){}
}
