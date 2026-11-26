package com.aistock.scheduler.task;
import com.aistock.common.*;
import java.time.*;import java.util.*;import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
import static com.aistock.scheduler.task.ComparisonTaskMysqlTest.*;
@Tag("mysql")
class ComparisonTaskWorkerTest {
 @Test void fourFlagCombinationsActuallySelectOnlyEnabledTaskTypes(){
  for(boolean old:new boolean[]{false,true})for(boolean compare:new boolean[]{false,true}){
   var f=new TaskMysqlTest();var r=f.repo();var raw=r.create("SH600276",DAY,"old",NOW);var ct=r.createComparison(snapshot(NOW),DAY,3,NOW);
   var m=mock(MarketRefreshClient.class);var receipt=mock(MarketReceiptClient.class);var p=mock(PredictionTaskClient.class);var c=mock(ComparisonTaskClient.class);
   when(m.refresh(any(),any())).thenAnswer(x->ApiResponse.success(new MarketRefreshClient.Result(((MarketRefreshClient.Request)x.getArgument(0)).operationId(),"AVAILABLE",UUID.randomUUID().toString(),null),"t"));
   when(c.execute(any(),any())).thenAnswer(x->new MarketRefreshClient.Result(((TaskRepository.Claim)x.getArgument(0)).attemptId(),"AVAILABLE",UUID.randomUUID().toString(),null));
   var w=new TaskWorker(r,m,receipt,p,c,Clock.fixed(NOW,ZoneOffset.UTC),"fixture",old,compare);w.tick();w.tick();
   assertEquals(old?"SUCCEEDED":"QUEUED",r.get(raw.taskId()).state());
   assertEquals(compare?"SUCCEEDED":"QUEUED",r.get(ct.taskId()).state());
   verify(m,times(old?1:0)).refresh(any(),any());verify(c,times(compare?1:0)).execute(any(),any());
  }
 }
 @Test void lostResponseReconcilesOriginalSuccessWithoutSecondAttempt(){
  var f=new TaskMysqlTest();var r=f.repo();var t=r.createComparison(snapshot(NOW),DAY,3,NOW);var c=mock(ComparisonTaskClient.class);
  var version=UUID.randomUUID().toString();when(c.execute(any(),any())).thenThrow(new IllegalStateException("lost"));
  when(c.receipt(any())).thenAnswer(x->new MarketRefreshClient.Result(((TaskRepository.Claim)x.getArgument(0)).attemptId(),"AVAILABLE",version,null));
  var w=new TaskWorker(r,mock(MarketRefreshClient.class),mock(MarketReceiptClient.class),mock(PredictionTaskClient.class),c,Clock.fixed(NOW,ZoneOffset.UTC),"fixture",false,true);
  w.tick();w.tick();assertEquals("SUCCEEDED",r.get(t.taskId()).state());assertEquals(version,r.get(t.taskId()).versionId());
  assertEquals(1,r.attempts(t.taskId()).size());verify(c,times(1)).execute(any(),any());
 }
 @Test void unknownUnavailableReceiptNeverBlindlyRepostsAndMissingExpiresWithinBudget(){
  var f=new TaskMysqlTest();var r=f.repo();var t=r.createComparison(snapshot(NOW),DAY,1,NOW);var c=mock(ComparisonTaskClient.class);var clock=new ComparisonUpdatePlanTest.MutableClock(NOW);
  when(c.execute(any(),any())).thenThrow(new IllegalStateException("lost"));
  when(c.receipt(any())).thenThrow(new ComparisonTaskClient.MissingReceipt());
  var w=new TaskWorker(r,mock(MarketRefreshClient.class),mock(MarketReceiptClient.class),mock(PredictionTaskClient.class),c,clock,"fixture",false,true);
  w.tick();assertEquals(1,r.attempts(t.taskId()).size());assertEquals("RECONCILING",r.get(t.taskId()).state());
  clock.now=NOW.plusSeconds(91);w.tick();assertEquals("FAILED",r.get(t.taskId()).state());assertEquals("ATTEMPT_EXPIRED",r.get(t.taskId()).errorCode());
  w.tick();verify(c,times(1)).execute(any(),any());
 }
}
