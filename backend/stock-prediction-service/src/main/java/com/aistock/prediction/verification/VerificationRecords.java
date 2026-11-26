package com.aistock.prediction.verification;
import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.util.List;
public final class VerificationRecords {
 private VerificationRecords(){}
 public static final String RULE="origin-share-close-v1";
 public enum PointState {WAITING,VERIFIED,MISSING,SUSPENDED,CALENDAR_CHANGED}
 public record ObservedDay(LocalDate date,PointState state,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal rawClose,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal backwardClose){}
 public record Evidence(String versionId,String instrumentId,String provider,String definition,LocalDate originDate,List<LocalDate> targetDates,LocalDate dueThrough,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal originRawClose,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal originBackwardClose,List<ObservedDay> observations){
  public Evidence {if(targetDates!=null)targetDates=List.copyOf(targetDates);if(observations!=null)observations=List.copyOf(observations);}
 }
 public record Point(int step,LocalDate targetDate,PointState state,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal referencePrice,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal actualPrice,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal signedError,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal absoluteError,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal normalizedAbsoluteError,Integer predictedDirection,Integer actualDirection,Boolean directionHit,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal baselineNormalizedAbsoluteError){}
 public record Metrics(@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal priceMae,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal priceRmse,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal ratioMae,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal ratioRmse,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal directionAccuracy,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal baselineRatioMae,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal baselineRatioRmse,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal relativeMaeImprovement){}
 public record Result(String ruleVersion,String evidenceVersionId,String state,int dueCount,int verifiedCount,int missingCount,boolean originRawRevised,List<Point> points,Metrics metrics){public Result {points=List.copyOf(points);}}
}
