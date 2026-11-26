package com.aistock.prediction.verification;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.*;
import java.util.*;
import static com.aistock.prediction.verification.VerificationRecords.*;
/** Pure evaluation of a frozen forecast; never fetches data or re-runs a model. */
public final class VerificationCalculator {
 private static final int SCALE=18;
 private static final RoundingMode ROUND=RoundingMode.HALF_UP;
 private static final MathContext ROOT=new MathContext(28,ROUND);
 private final VerificationValidator validator=new VerificationValidator();
 public Result calculate(JsonNode snapshot,Evidence evidence){
  var fixed=validator.validate(snapshot,evidence);var origin=fixed.origin().setScale(6,ROUND);
  if(origin.signum()<=0)throw VerificationValidator.invalid();
  var q0=divide(evidence.originBackwardClose(),evidence.originRawClose());
  if(q0.signum()<=0)throw VerificationValidator.invalid();
  var actuals=new HashMap<java.time.LocalDate,ObservedDay>();evidence.observations().forEach(d->actuals.put(d.date(),d));
  var points=new ArrayList<Point>();int due=0,valid=0,hits=0;
  BigDecimal priceSum=BigDecimal.ZERO,priceSquared=BigDecimal.ZERO,ratioSum=BigDecimal.ZERO,ratioSquared=BigDecimal.ZERO,baseSum=BigDecimal.ZERO,baseSquared=BigDecimal.ZERO;
  for(int i=0;i<5;i++){
   var date=evidence.targetDates().get(i);var predicted=fixed.prices().get(i).setScale(6,ROUND);if(predicted.signum()<=0)throw VerificationValidator.invalid();
   if(date.isAfter(evidence.dueThrough())){points.add(empty(i,date,PointState.WAITING,predicted));continue;}
   due++;var observed=actuals.get(date);
   if(observed==null||observed.state()!=PointState.VERIFIED){points.add(empty(i,date,observed==null?PointState.MISSING:observed.state(),predicted));continue;}
   var qt=divide(observed.backwardClose(),observed.rawClose());
   if(qt.signum()<=0)throw VerificationValidator.invalid();
   var actual=divide(observed.rawClose().multiply(qt),q0).setScale(6,ROUND);if(actual.signum()<=0)throw VerificationValidator.invalid();
   var error=predicted.subtract(actual).abs();var normalized=divide(error,fixed.origin());var baseline=divide(origin.subtract(actual).abs(),fixed.origin());
   int predictedDirection=predicted.compareTo(origin),actualDirection=actual.compareTo(origin);boolean hit=predictedDirection==actualDirection;
   valid++;if(hit)hits++;priceSum=priceSum.add(error);priceSquared=priceSquared.add(error.multiply(error));ratioSum=ratioSum.add(normalized);ratioSquared=ratioSquared.add(normalized.multiply(normalized));baseSum=baseSum.add(baseline);baseSquared=baseSquared.add(baseline.multiply(baseline));
   points.add(new Point(i+1,date,PointState.VERIFIED,predicted,actual,predicted.subtract(actual),error,normalized,predictedDirection,actualDirection,hit,baseline));
  }
  Metrics metrics;
  if(valid==0)metrics=new Metrics(null,null,null,null,null,null,null,null);
  else {
   var count=BigDecimal.valueOf(valid);var mae=divide(ratioSum,count);var baseline=divide(baseSum,count);
   metrics=new Metrics(divide(priceSum,count),rmse(priceSquared,count),mae,rmse(ratioSquared,count),divide(BigDecimal.valueOf(hits),count),baseline,rmse(baseSquared,count),baseline.signum()==0?null:divide(baseline.subtract(mae),baseline));
  }
  return new Result(RULE,evidence.versionId(),valid==5?"COMPLETE":valid==0?"PENDING":"PARTIAL",due,valid,due-valid,evidence.originRawClose().compareTo(fixed.origin())!=0,points,metrics);
 }
 private Point empty(int index,java.time.LocalDate date,PointState state,BigDecimal predicted){return new Point(index+1,date,state,predicted,null,null,null,null,null,null,null,null);}
 private BigDecimal divide(BigDecimal a,BigDecimal b){return a.divide(b,SCALE,ROUND);}
 private BigDecimal rmse(BigDecimal squares,BigDecimal count){return squares.divide(count,new MathContext(50,ROUND)).sqrt(ROOT).setScale(SCALE,ROUND);}
}
