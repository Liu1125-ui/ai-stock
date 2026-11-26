package com.aistock.market.ingestion;
import com.aistock.market.quotation.RawBar;
import com.aistock.market.quotation.TradingDay;
import java.time.Instant;
import java.util.List;
public record MarketCandidate(String operationId,String instrumentId,String ticker,String displayName,
    boolean displayNameVerified,String listingStatus,String provider,String priceBasis,Instant fetchedAt,
    String sourceVersionId,String sourcePayloadHash,List<RawBar> bars,List<TradingDay> calendar,Long expectedRevision) {}
