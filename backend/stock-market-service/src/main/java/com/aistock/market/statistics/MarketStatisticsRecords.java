package com.aistock.market.statistics;
import com.aistock.common.statistics.StatisticsMeta;import java.time.*;import java.util.List;
public final class MarketStatisticsRecords {
 private MarketStatisticsRecords(){}
 public record HeadItem(String instrumentId,String provider,String priceBasis,String versionId,String status,String quality,Instant checkedAt,Instant fetchedAt,Instant publishedAt,LocalDate coverageEnd,String lastErrorCode){}
 public record MarketSummary(StatisticsMeta meta,String scope,String instrumentCount,String availableHeads,List<HeadItem> items,boolean hasMore){public MarketSummary{items=List.copyOf(items);}}
}
