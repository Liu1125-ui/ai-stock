package com.aistock.prediction.portfolioanalysis;
import java.time.*;import java.util.*;
public final class PortfolioRecords {
 public static final String FORMULA="portfolio-valuation-v1";
 private PortfolioRecords(){}
 public record SnapshotRequest(String token){@Override public String toString(){return "SnapshotRequest[redacted]";}}
 public record SessionToken(String value){@Override public String toString(){return "SessionToken[redacted]";}}
 public record PositionSnapshot(UUID ownerId,String sourceService,String bookRevision,List<PositionItem> items){}
 public record PositionRevision(UUID ownerId,String sourceService,String bookRevision){}
 public record PositionItem(UUID positionId,String instrumentId,String quantity,String averageCost,String revision){}
 public record QuoteRequest(LocalDate asOfDate,List<String> instrumentIds){}
 public record Quote(String instrumentId,String status,String reason,String close,LocalDate priceDate,UUID versionId,String contentHash,String provider,String priceBasis,String freshness,UUID calendarVersionId,String calendarHash,LocalDate checkedThrough,boolean retainedPrice){}
 public record QuoteBatch(LocalDate asOfDate,Instant capturedAt,List<Quote> items){}
 public record Request(UUID requestId){}
 public record Operation(UUID requestId,String state,UUID analysisId,String errorCode,Instant acceptedAt,Instant expiresAt){}
 public record Creation(Operation operation,boolean replayed){}
 public record Claim(boolean execute,UUID generation,Operation operation,boolean replayed){}
 public record MoneyLine(UUID positionId,String instrumentId,String quantity,String averageCost,String positionRevision,Quote quote,String costAmount,String marketValue,String floatingProfit,String returnPercent,String weightPercent,Boolean concentrationTriggered){}
 public record Amounts(String costAmount,String marketValue,String floatingProfit,String returnPercent){}
 public record Calculation(String formulaVersion,String bookRevision,LocalDate asOfDate,Instant capturedAt,String state,String freshness,boolean mixedDates,int positionCount,int valuedCount,List<MoneyLine> lines,Amounts subtotal,Amounts totals,String concentrationThresholdPercent,String sourceHash){}
}
