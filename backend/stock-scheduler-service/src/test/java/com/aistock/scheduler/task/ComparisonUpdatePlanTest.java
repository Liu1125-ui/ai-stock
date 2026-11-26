package com.aistock.scheduler.task;
import java.time.*;import java.util.*;import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
import static com.aistock.scheduler.task.ComparisonTaskMysqlTest.*;
class ComparisonUpdatePlanTest {
 static final class MutableClock extends Clock {
  Instant now;MutableClock(Instant now){this.now=now;}public Instant instant(){return now;}
  public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}
 }
 static ComparisonTaskRecords.Item item(String state){
  var s=snapshot(NOW);return new ComparisonTaskRecords.Item(s.instrumentId(),state,"READY".equals(state)?null:state,s.rawVersionId(),s.rawContentHash(),s.start(),s.end(),null,null);
 }
 @Test void beforeCutoffAndDisabledNeverReadOrEnqueue(){
  var r=mock(TaskRepository.class);var c=mock(ComparisonTaskClient.class);
  for(var plan:List.of(new ComparisonUpdatePlan(r,c,Clock.fixed(Instant.parse("2026-10-13T11:59:59Z"),ZoneOffset.UTC),"fixture",true,"SH600030",3),
   new ComparisonUpdatePlan(r,c,Clock.fixed(NOW,ZoneOffset.UTC),"fixture",false,"SH600030",3))){
   assertFalse(plan.runOnce());
  }verifyNoInteractions(r,c);
 }
 @Test void readyOnlyUsesOptionsAndEnqueuesFixedContext(){
  var r=mock(TaskRepository.class);var c=mock(ComparisonTaskClient.class);var clock=Clock.fixed(NOW,ZoneOffset.UTC);
  when(c.options(List.of("SH600030"))).thenReturn(new ComparisonTaskRecords.Options(NOW,"baostock-comparison-backward-close-v1",List.of(item("READY"))));
  var plan=new ComparisonUpdatePlan(r,c,clock,"fixture",true,"SH600030",1);
  assertTrue(plan.runOnce());
  verify(r).createComparison(argThat(s->s.instrumentId().equals("SH600030")&&s.start().equals(DAY.minusYears(1))&&s.planAsOf().equals(NOW)),eq(DAY),eq(1),eq(NOW));
  verify(c,never()).execute(any(),any());
 }
 @Test void currentAndUnavailableDoNotEnqueue(){
  var r=mock(TaskRepository.class);var c=mock(ComparisonTaskClient.class);var plan=new ComparisonUpdatePlan(r,c,Clock.fixed(NOW,ZoneOffset.UTC),"fixture",true,"SH600030",3);
  for(var state:List.of("CURRENT","RAW_LAGGING","CALENDAR_UNAVAILABLE")){
   var i=item(state);
   if(state.equals("CURRENT"))i=new ComparisonTaskRecords.Item(i.instrumentId(),state,null,i.rawVersionId(),i.rawContentHash(),i.rangeStart(),i.targetDate(),UUID.randomUUID(),i.targetDate());
   when(c.options(any())).thenReturn(new ComparisonTaskRecords.Options(NOW,"baostock-comparison-backward-close-v1",List.of(i)));
   assertFalse(plan.runOnce());
  }verifyNoInteractions(r);
 }
 @Test void crossingMidnightOrStaleAsOfCannotCreateNewCycle(){
  var r=mock(TaskRepository.class);var c=mock(ComparisonTaskClient.class);var clock=new MutableClock(Instant.parse("2026-10-13T15:59:59Z"));
  when(c.options(any())).thenAnswer(call->{var asOf=clock.instant();clock.now=clock.now.plusSeconds(2);
   return new ComparisonTaskRecords.Options(asOf,"baostock-comparison-backward-close-v1",List.of(item("READY")));});
  var plan=new ComparisonUpdatePlan(r,c,clock,"fixture",true,"SH600030",3);assertFalse(plan.runOnce());verifyNoInteractions(r);
 }
 @Test void invalidInstrumentConfigurationFailsAtStartup(){
  for(var ids:List.of("","SH600030,SH600030","SH600030,","BAD","SH600030,".repeat(51)))
   assertThrows(IllegalArgumentException.class,()->new ComparisonUpdatePlan(mock(TaskRepository.class),mock(ComparisonTaskClient.class),Clock.fixed(NOW,ZoneOffset.UTC),"fixture",true,ids,3));
 }
}
