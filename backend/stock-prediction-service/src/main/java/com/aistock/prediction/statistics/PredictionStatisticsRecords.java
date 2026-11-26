package com.aistock.prediction.statistics;
import com.aistock.common.statistics.StatisticsMeta;import static com.aistock.common.statistics.StatisticsRecords.*;import java.time.LocalDate;import java.util.List;
public final class PredictionStatisticsRecords {
 private PredictionStatisticsRecords(){}
 public record PredictionDay(LocalDate date,String accepted,String succeeded,String failed,String running){}
 public record ModelUsage(String modelId,String artifactId,String label,String acceptedRequests,String successfulRequests,String usagePercent){}
 public record PredictionsSummary(StatisticsMeta meta,String acceptedRequests,List<CountByKey> stateCounts,List<PredictionDay> requestDaily,List<ModelUsage> modelUsage){public PredictionsSummary{stateCounts=List.copyOf(stateCounts);requestDaily=List.copyOf(requestDaily);modelUsage=List.copyOf(modelUsage);}}
}
