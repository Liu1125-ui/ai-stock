package com.aistock.scheduler.statistics;
import com.aistock.common.statistics.StatisticsMeta;import static com.aistock.common.statistics.StatisticsRecords.*;import java.util.List;
public final class TaskStatisticsRecords {
 private TaskStatisticsRecords(){}
 public record TasksSummary(StatisticsMeta meta,String taskTotal,List<CountByKey> taskStateCounts,List<CountByKey> taskTypeCounts,List<DailyCount> taskDaily,String attemptTotal,List<CountByKey> attemptStateCounts,DurationSummary attemptDuration,String unfinishedAttempts,DurationSummary firstQueueWait){public TasksSummary{taskStateCounts=List.copyOf(taskStateCounts);taskTypeCounts=List.copyOf(taskTypeCounts);taskDaily=List.copyOf(taskDaily);attemptStateCounts=List.copyOf(attemptStateCounts);}}
}
