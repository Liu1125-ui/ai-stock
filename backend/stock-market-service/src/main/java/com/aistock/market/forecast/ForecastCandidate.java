package com.aistock.market.forecast;
import com.aistock.market.quotation.RawBar;
import com.aistock.market.quotation.TradingDay;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
public record ForecastCandidate(String operationId,String instrumentId,String rawVersionId,String provider,
 String definition,Instant fetchedAt,String sourcePayloadHash,List<PairedBar> bars,List<TradingDay> calendar) {
 public record PairedBar(RawBar raw,BigDecimal backwardClose) {}
}
