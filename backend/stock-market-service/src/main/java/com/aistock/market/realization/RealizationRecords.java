package com.aistock.market.realization;
import com.aistock.market.forecast.ForecastCandidate;
import com.aistock.market.quotation.RawBar;
import com.aistock.market.quotation.TradingDay;
import java.time.*;import java.util.*;
public final class RealizationRecords {
 private RealizationRecords(){}
 public static final String DEFINITION="baostock-backward-close-over-raw-v1";
 public record Request(UUID operationId,String instrumentId,LocalDate originDate,List<LocalDate> targetDates,LocalDate dueThrough,Instant leaseUntil){}
 public record Head(String versionId,String contentHash,List<RawBar> bars){}
 public record Source(String state,ForecastCandidate candidate,String errorCode){}
 public record Receipt(UUID operationId,String state,String versionId,String errorCode){}
 public record Day(LocalDate date,String state,String rawClose,String backwardClose){}
 public record Evidence(String realizationVersionId,String instrumentId,String provider,String definition,LocalDate originDate,List<LocalDate> targetDates,LocalDate dueThrough,String rawVersionId,String rawContentHash,String sourcePayloadHash,String calendarHash,String contentHash,Instant capturedAt,String originRawClose,String originBackwardClose,List<Day> observations,List<TradingDay> calendar){
  public Evidence {targetDates=List.copyOf(targetDates);observations=List.copyOf(observations);calendar=List.copyOf(calendar);}
 }
}
