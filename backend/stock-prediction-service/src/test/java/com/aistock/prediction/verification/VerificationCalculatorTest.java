package com.aistock.prediction.verification;
import com.fasterxml.jackson.databind.*;
import com.aistock.common.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static java.math.RoundingMode.HALF_UP;
import static com.aistock.prediction.verification.VerificationRecords.*;
class VerificationCalculatorTest {
 @Test void smallNonzeroSingleSampleRmseEqualsMae()throws Exception{
  var s=snapshot("2000.000001");((com.fasterxml.jackson.databind.node.ObjectNode)s.get("input")).put("originRawClose","2000");((com.fasterxml.jackson.databind.node.ObjectNode)s.get("prediction")).put("originRawClose","2000");
  var r=new VerificationCalculator().calculate(s,evidence(dates.get(0),List.of(day(0,"2000","2000")),"2000","2000"));assertEquals(n("0.000000000500000000"),r.metrics().ratioMae());assertEquals(r.metrics().ratioMae(),r.metrics().ratioRmse());
 }
 @Test void ratioDenominatorPreservesOriginalOriginPrecision()throws Exception{
  var s=snapshot("101");((com.fasterxml.jackson.databind.node.ObjectNode)s.get("input")).put("originRawClose","100.0000004");((com.fasterxml.jackson.databind.node.ObjectNode)s.get("prediction")).put("originRawClose","100.0000004");
  var r=new VerificationCalculator().calculate(s,evidence(dates.get(0),List.of(day(0,"100","100")),"100.0000004","100.0000004"));assertEquals(n("1").divide(n("100.0000004"),18,HALF_UP),r.metrics().ratioMae());
 }
 @Test void multipleValidSamplesUseTheirOwnCountAndNonzeroRmse()throws Exception{
  var r=calculate("100",evidence(dates.get(1),List.of(day(0,"102","102"),day(1,"104","104")),"100","100"));assertEquals(n("0.030000000000000000"),r.metrics().ratioMae());assertEquals(n("0.031622776601683793"),r.metrics().ratioRmse());assertEquals(2,r.verifiedCount());
 }
 @Test void oversizedStepCannotWrapIntoValidInteger()throws Exception{
  var s=snapshot("100");((com.fasterxml.jackson.databind.node.ObjectNode)s.get("prediction").get("points").get(0)).put("step",4294967297L);
  assertThrows(BusinessException.class,()->new VerificationCalculator().calculate(s,evidence(dates.get(0),List.of(day(0,"100","100")),"100","100")));
 }
 @Test void signedErrorsAndWirePricesPreserveExactDecimals()throws Exception{
  var r=calculate("100",evidence(dates.get(0),List.of(day(0,"102","102")),"100","100"));
  var json=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).valueToTree(r);
  assertEquals("-2.000000",json.path("points").get(0).path("signedError").asText());
  assertTrue(json.path("points").get(0).path("actualPrice").isTextual());assertTrue(json.path("metrics").path("ratioMae").isTextual());
 }
 private final ObjectMapper mapper=new ObjectMapper();
 private final LocalDate origin=LocalDate.parse("2026-09-30");
 private final List<LocalDate> dates=List.of("2026-10-08","2026-10-09","2026-10-12","2026-10-13","2026-10-14").stream().map(LocalDate::parse).toList();
 private BigDecimal n(String v){return new BigDecimal(v);}
 private JsonNode snapshot(String price)throws Exception {
  var root=mapper.createObjectNode();var input=root.putObject("input");input.put("instrumentId","SH600030").put("originDate",origin.toString()).put("originRawClose","100");var targets=input.putArray("targetDates");dates.forEach(d->targets.add(d.toString()));
  var prediction=root.putObject("prediction");prediction.put("instrumentId","SH600030").put("originDate",origin.toString()).put("originRawClose","100").put("referenceBasis","ORIGIN_SHARE_BASIS").put("formalInference",true).put("validationOnly",false);var points=prediction.putArray("points");for(int i=0;i<5;i++)points.addObject().put("step",i+1).put("targetDate",dates.get(i).toString()).put("referencePrice",price);
  return root;
 }
 private ObservedDay day(int index,String raw,String backward){return new ObservedDay(dates.get(index),PointState.VERIFIED,n(raw),n(backward));}
 private Evidence evidence(LocalDate through,List<ObservedDay> days,String originRaw,String originBackward){return new Evidence("11111111-1111-1111-1111-111111111111","SH600030","baostock","baostock-backward-close-over-raw-v1",origin,dates,through,n(originRaw),n(originBackward),days);}
 private Result calculate(String predicted,Evidence evidence)throws Exception{return new VerificationCalculator().calculate(snapshot(predicted),evidence);}
 @Test void splitIsComparedOnOriginalShareBasisAndFlatCountsCorrect()throws Exception{
  var days=new ArrayList<ObservedDay>();for(int i=0;i<5;i++)days.add(day(i,"50","100"));
  var result=calculate("100",evidence(dates.get(4),days,"100","100"));
  assertEquals("COMPLETE",result.state());assertEquals(5,result.verifiedCount());assertEquals(n("100.000000"),result.points().get(0).actualPrice());assertEquals(0,result.metrics().ratioMae().signum());assertEquals(n("1.000000000000000000"),result.metrics().directionAccuracy());assertNull(result.metrics().relativeMaeImprovement());
 }
 @Test void partialDataDoesNotTreatMissingSuspendedOrFutureAsCorrect()throws Exception{
  var days=List.of(day(0,"102","102"),new ObservedDay(dates.get(1),PointState.MISSING,null,null),new ObservedDay(dates.get(2),PointState.SUSPENDED,n("100"),n("100")));
  var result=calculate("105",evidence(dates.get(2),days,"100","100"));
  assertEquals("PARTIAL",result.state());assertEquals(3,result.dueCount());assertEquals(1,result.verifiedCount());assertEquals(2,result.missingCount());assertEquals(PointState.WAITING,result.points().get(4).state());assertEquals(n("0.030000000000000000"),result.metrics().ratioMae());assertEquals(n("0.020000000000000000"),result.metrics().baselineRatioMae());assertEquals(n("-0.500000000000000000"),result.metrics().relativeMaeImprovement());
 }
 @Test void noSamplesHasNullMetricsRatherThanZeroAccuracy()throws Exception{
  var result=calculate("100",evidence(origin,List.of(),"100","100"));assertEquals("PENDING",result.state());assertEquals(0,result.dueCount());assertNull(result.metrics().directionAccuracy());assertNull(result.metrics().ratioMae());
 }
 @Test void calendarChangesAndSuspensionAreNotInTheDenominator()throws Exception{
  var result=calculate("100",evidence(dates.get(4),List.of(new ObservedDay(dates.get(0),PointState.CALENDAR_CHANGED,null,null)),"100","100"));assertEquals(5,result.dueCount());assertEquals(5,result.missingCount());assertNull(result.metrics().ratioRmse());assertEquals(PointState.CALENDAR_CHANGED,result.points().get(0).state());
 }
 @Test void adjustmentScaleCancelsAndLaterOriginCorrectionIsFlagged()throws Exception{
  var a=calculate("105",evidence(dates.get(0),List.of(day(0,"102","204")),"100","200"));var b=calculate("105",evidence(dates.get(0),List.of(day(0,"102","2040")),"100","2000"));assertEquals(a.metrics(),b.metrics());
  var corrected=calculate("105",evidence(dates.get(0),List.of(day(0,"102","102")),"99","99"));assertTrue(corrected.originRawRevised());assertEquals(n("0.030000000000000000"),corrected.metrics().ratioMae());
 }
 @Test void precisionRuleAppliesBeforeDirection()throws Exception{
  var r=calculate("100.0000004",evidence(dates.get(0),List.of(day(0,"100.0000004","100.0000004")),"100","100"));assertEquals(0,r.points().get(0).predictedDirection());assertEquals(0,r.points().get(0).actualDirection());
 }
 @Test void wrongIdentityFutureExtraDuplicateAndInvalidActualReject()throws Exception{
  var valid=evidence(dates.get(0),List.of(day(0,"102","102")),"100","100");
  var wrong=snapshot("105");((com.fasterxml.jackson.databind.node.ObjectNode)wrong.get("input")).put("instrumentId","SZ000002");assertThrows(BusinessException.class,()->new VerificationCalculator().calculate(wrong,valid));
  assertThrows(BusinessException.class,()->calculate("105",evidence(dates.get(0),List.of(day(1,"102","102")),"100","100")));
  assertThrows(BusinessException.class,()->calculate("105",evidence(dates.get(0),List.of(day(0,"102","102"),day(0,"102","102")),"100","100")));
  assertThrows(BusinessException.class,()->calculate("105",evidence(dates.get(0),List.of(day(0,"0","102")),"100","100")));
 }
 @Test void negativeAndNonFormalPredictionsReject()throws Exception{
  var valid=evidence(dates.get(0),List.of(day(0,"102","102")),"100","100");assertThrows(BusinessException.class,()->calculate("-1",valid));var s=snapshot("105");((com.fasterxml.jackson.databind.node.ObjectNode)s.get("prediction")).put("validationOnly",true);assertThrows(BusinessException.class,()->new VerificationCalculator().calculate(s,valid));
 }
}
