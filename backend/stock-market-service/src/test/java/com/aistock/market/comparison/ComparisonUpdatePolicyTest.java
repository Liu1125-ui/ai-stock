package com.aistock.market.comparison;
import com.aistock.common.BusinessException;
import com.aistock.market.quotation.*;
import java.time.*;import java.math.BigDecimal;import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.aistock.market.comparison.ComparisonUpdateSamples.*;

class ComparisonUpdatePolicyTest {
 @Test void onlyCompletedSessionAtShanghaiCutoff(){
  var days=calendar(END.minusDays(12),END);
  assertEquals(END.minusDays(1),ComparisonUpdatePolicy.target(days,Instant.parse("2026-10-13T11:59:59Z"),20));
  assertEquals(END,ComparisonUpdatePolicy.target(days,NOW.instant(),20));
 }
 @Test void weekendAndHolidayUseLastOpenSession(){
  var d=calendar(LocalDate.of(2026,10,9),LocalDate.of(2026,10,12));
  assertEquals(LocalDate.of(2026,10,9),ComparisonUpdatePolicy.target(d,Instant.parse("2026-10-11T13:00:00Z"),20));
  var holiday=List.of(new TradingDay(END.minusDays(1),true),new TradingDay(END,false));
  assertEquals(END.minusDays(1),ComparisonUpdatePolicy.target(holiday,NOW.instant(),20));
 }
 @Test void gapsDuplicatesAndNoCompletedOpenSessionAreRejected(){
  for(var d:List.of(List.of(new TradingDay(END.minusDays(2),true),new TradingDay(END,true)),
    List.of(new TradingDay(END,true),new TradingDay(END,true)),List.of(new TradingDay(END,false)))){
   assertThrows(BusinessException.class,()->ComparisonUpdatePolicy.target(d,NOW.instant(),20));
  }
 }
 @Test void leapYearWindowUsesCalendarYear(){
  var leap=LocalDate.of(2028,2,29);
  assertEquals(LocalDate.of(2027,2,28),ComparisonUpdatePolicy.target(calendar(leap.minusDays(3),leap),
   Instant.parse("2028-02-29T12:00:00Z"),20).minusYears(1));
 }
 @Test void numericScaleAndChangesOutsideRequestedWindowDoNotTriggerCollection(){
  var a=bars(START,END,"10.0",false); var b=bars(START.minusDays(2),END,"10.000",false);
  assertTrue(ComparisonUpdatePolicy.sameWindow(a,calendar(START,END),b,calendar(START.minusDays(2),END),START,END));
 }
 @Test void priceVolumeStatusAndCalendarEachAffectCurrent(){
  var a=bars(START,END,"10",false);
  assertFalse(ComparisonUpdatePolicy.sameWindow(a,calendar(START,END),bars(START,END,"11",false),calendar(START,END),START,END));
  assertFalse(ComparisonUpdatePolicy.sameWindow(a,calendar(START,END),bars(START,END,"10",true),calendar(START,END),START,END));
  var b=new ArrayList<>(a);var last=b.get(b.size()-1);
  b.set(b.size()-1,new RawBar(last.date(),last.open(),last.high(),last.low(),last.close(),new BigDecimal("101"),last.amountYuan(),1,null));
  assertFalse(ComparisonUpdatePolicy.sameWindow(a,calendar(START,END),b,calendar(START,END),START,END));
  var c=new ArrayList<>(calendar(START,END));c.set(0,new TradingDay(START,false));
  assertFalse(ComparisonUpdatePolicy.sameWindow(a,calendar(START,END),a,c,START,END));
 }
 @Test void malformedWindowCalendarCannotBeDeclaredCurrent(){
  var a=bars(START,END,"10",false);
  assertThrows(BusinessException.class,()->ComparisonUpdatePolicy.sameWindow(a,calendar(START,END).subList(1,366),a,calendar(START,END),START,END));
  var duplicate=new ArrayList<>(a);duplicate.add(a.get(0));
  assertThrows(BusinessException.class,()->ComparisonUpdatePolicy.sameWindow(a,calendar(START,END),duplicate,calendar(START,END),START,END));
 }
}
