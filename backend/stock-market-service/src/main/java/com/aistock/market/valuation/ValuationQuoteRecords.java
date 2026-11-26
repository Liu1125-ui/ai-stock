package com.aistock.market.valuation;
import java.time.*;import java.util.*;
public final class ValuationQuoteRecords {
 private ValuationQuoteRecords(){}
 public record QuoteRequest(LocalDate asOfDate,List<String> instrumentIds){}
 public record Quote(String instrumentId,String status,String reason,String close,LocalDate priceDate,
  UUID versionId,String contentHash,String provider,String priceBasis,String freshness,
  UUID calendarVersionId,String calendarHash,LocalDate checkedThrough,boolean retainedPrice){}
 public record QuoteBatch(LocalDate asOfDate,Instant capturedAt,List<Quote> items){}
}
