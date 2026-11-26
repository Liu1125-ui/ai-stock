package com.aistock.market.alert;
import com.aistock.market.forecast.ForecastCandidate;
import com.aistock.market.quotation.TradingDay;
import java.time.*;import java.util.*;
public final class AlertEvidenceRecords {
 private AlertEvidenceRecords(){}
 public static final String DEFINITION="alert-daily-evidence-v1";
 public record Request(UUID operationId,String instrumentId,UUID rawVersionId,LocalDate tradeDate,Instant leaseUntil){}
 public record Receipt(UUID operationId,String state,UUID evidenceId,String errorCode){}
 public record Pair(String previousBackwardClose,String currentBackwardClose){}
 public record Source(String state,ForecastCandidate candidate,String errorCode){}
 public record Evidence(UUID id,String instrumentId,String provider,String definition,LocalDate tradeDate,LocalDate previousTradeDate,UUID rawVersionId,String rawContentHash,UUID calendarVersionId,String calendarHash,List<TradingDay> calendarDays,String priceState,String rawClose,String changeState,String previousBackwardClose,String currentBackwardClose,String sourcePayloadHash,Instant capturedAt,String contentHash){
  public Evidence {calendarDays=List.copyOf(calendarDays);}
 }
}
