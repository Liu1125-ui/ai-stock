package com.aistock.scheduler.task;
import com.aistock.common.BusinessException;
import java.time.*;import java.util.*;import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("mysql")
class ComparisonTaskMysqlTest {
 static final Instant NOW=Instant.parse("2026-10-13T12:05:00Z");
 static final LocalDate DAY=LocalDate.of(2026,10,13);
 static final Set<String> TYPES=Set.of("COMPARISON_UPDATE");
 static ComparisonTaskRecords.Snapshot snapshot(Instant now){
  return new ComparisonTaskRecords.Snapshot("SH600030",UUID.randomUUID(),"a".repeat(64),DAY.minusYears(1),DAY,"baostock-comparison-backward-close-v1",now);
 }
 @Test void oneDailyContextSurvivesNewRawAndLimitConfiguration(){
  var f=new TaskMysqlTest();var repo=f.repo();var first=snapshot(NOW);
  var task=repo.createComparison(first,DAY,1,NOW);var changed=snapshot(NOW.plusMillis(1));
  assertEquals(task.taskId(),repo.createComparison(changed,DAY,3,NOW.plusMillis(1)).taskId());
  assertEquals(first,repo.comparisonContext(task.taskId()).snapshot());assertEquals(1,repo.comparisonContext(task.taskId()).attemptLimit());
  assertEquals(1,repo.list().size());assertNull(repo.claim(NOW));assertEquals(task.taskId(),repo.claim(NOW,TYPES).taskId());
  assertTrue(repo.pendingReceipts().isEmpty());assertEquals(1,repo.pendingReceipts(TYPES).size());
 }
 @RepeatedTest(10) void concurrentCreationAndClaimEachHaveOneOwner()throws Exception{
  var f=new TaskMysqlTest();var r=f.repo();var s=snapshot(NOW);var pool=Executors.newFixedThreadPool(2);
  try{
   var a=pool.submit(()->r.createComparison(s,DAY,3,NOW));var b=pool.submit(()->r.createComparison(s,DAY,3,NOW));
   var task=a.get(5,TimeUnit.SECONDS);assertEquals(task.taskId(),b.get(5,TimeUnit.SECONDS).taskId());
   var c=pool.submit(()->r.claim(NOW,TYPES));var d=pool.submit(()->r.claim(NOW,TYPES));
   var owners=new ArrayList<TaskRepository.Claim>();
   var first=c.get(5,TimeUnit.SECONDS);var second=d.get(5,TimeUnit.SECONDS);
   if(first!=null)owners.add(first);if(second!=null)owners.add(second);
   assertTrue(owners.size()<=1,"Concurrent transactions must never create duplicate owners");
   // SKIP LOCKED may transiently skip both scans; after both transactions end the original task must be claimable.
   if(owners.isEmpty()){
    var queued=f.jdbc.queryForMap("select * from scheduled_task where task_id=?",task.taskId());
    assertEquals("QUEUED",queued.get("state"));assertEquals(0,((Number)queued.get("attempt_count")).intValue());
    assertNull(queued.get("current_attempt"));assertNull(queued.get("lease_until"));
    assertEquals(0,f.jdbc.queryForObject("select count(*) from task_attempt",Integer.class));
    assertEquals(1,f.jdbc.queryForObject("select count(*) from comparison_update_context",Integer.class));
    var next=r.claim(NOW,TYPES);assertNotNull(next);owners.add(next);
   }
   assertEquals(1,owners.size());var owner=owners.get(0);assertEquals(task.taskId(),owner.taskId());
   var running=f.jdbc.queryForMap("select * from scheduled_task where task_id=?",task.taskId());
   assertEquals("RUNNING",running.get("state"));assertEquals(1,((Number)running.get("attempt_count")).intValue());
   assertEquals(owner.attemptId(),running.get("current_attempt"));
   assertEquals(1,f.jdbc.queryForObject("select count(*) from task_attempt where task_id=? and attempt_id=? and ordinal=1 and state='RUNNING'",Integer.class,task.taskId(),owner.attemptId()));
   assertNull(r.claim(NOW,TYPES));assertEquals(1,f.jdbc.queryForObject("select count(*) from task_attempt",Integer.class));
   assertEquals(1,f.jdbc.queryForObject("select count(*) from comparison_update_context",Integer.class));
  }finally{pool.shutdownNow();}
 }
 @Test void staleAsOfWrongCycleAndBadSnapshotRejected(){
  var f=new TaskMysqlTest();var r=f.repo();var s=snapshot(NOW);
  assertThrows(BusinessException.class,()->r.createComparison(s,DAY.minusDays(1),3,NOW));
  assertThrows(BusinessException.class,()->r.createComparison(snapshot(NOW.minusSeconds(6)),DAY,3,NOW));
  assertThrows(BusinessException.class,()->r.createComparison(s,DAY,4,NOW));
  assertThrows(BusinessException.class,()->r.createComparison(new ComparisonTaskRecords.Snapshot("BAD",s.rawVersionId(),s.rawContentHash(),s.start(),s.end(),s.definition(),NOW),DAY,3,NOW));
  assertThrows(BusinessException.class,()->r.createComparison(new ComparisonTaskRecords.Snapshot(s.instrumentId(),s.rawVersionId(),s.rawContentHash(),s.start().plusDays(1),s.end(),s.definition(),NOW),DAY,3,NOW));
  assertThrows(BusinessException.class,()->r.createComparison(new ComparisonTaskRecords.Snapshot(s.instrumentId(),s.rawVersionId(),s.rawContentHash(),s.start(),s.end(),"wrong",NOW),DAY,3,NOW));
  assertTrue(r.list().isEmpty());
 }
 @Test void threeAttemptsUseComparisonBackoffWithoutResettingHistory(){
  var f=new TaskMysqlTest();var r=f.repo();var t=r.createComparison(snapshot(NOW),DAY,3,NOW);
  var c=r.claim(NOW,TYPES);
  assertTrue(r.finish(c,new MarketRefreshClient.Result(c.attemptId(),"FAILED",null,"SOURCE_TIMEOUT"),NOW.plusSeconds(1)));
  assertNull(r.claim(NOW.plusSeconds(60),TYPES));
  c=r.claim(NOW.plusSeconds(901),TYPES);assertNotNull(c);
  r.finish(c,new MarketRefreshClient.Result(c.attemptId(),"FAILED",null,"SOURCE_BUSY"),NOW.plusSeconds(902));
  assertNull(r.claim(NOW.plusSeconds(1800),TYPES));
  c=r.claim(NOW.plusSeconds(2702),TYPES);assertNotNull(c);
  r.finish(c,new MarketRefreshClient.Result(c.attemptId(),"FAILED",null,"SOURCE_UNAVAILABLE"),NOW.plusSeconds(2703));
  assertEquals("FAILED",r.get(t.taskId()).state());assertEquals(3,r.attempts(t.taskId()).size());
  assertThrows(BusinessException.class,()->r.retry(t.taskId(),NOW.plusSeconds(2704)));
 }
 @Test void acceptanceLimitOneAndInvalidDataCannotBeManuallyRetried(){
  var f=new TaskMysqlTest();var r=f.repo();var t=r.createComparison(snapshot(NOW),DAY,1,NOW);var c=r.claim(NOW,TYPES);
  r.finish(c,new MarketRefreshClient.Result(c.attemptId(),"FAILED",null,"SOURCE_TIMEOUT"),NOW.plusSeconds(1));
  assertEquals("FAILED",r.get(t.taskId()).state());assertThrows(BusinessException.class,()->r.retry(t.taskId(),NOW.plusSeconds(2)));
  var other=new ComparisonTaskRecords.Snapshot("SH600276",UUID.randomUUID(),"b".repeat(64),DAY.minusYears(1),DAY,"baostock-comparison-backward-close-v1",NOW);
  var bad=r.createComparison(other,DAY,3,NOW);c=r.claim(NOW,TYPES);
  r.finish(c,new MarketRefreshClient.Result(c.attemptId(),"FAILED",null,"SOURCE_INVALID"),NOW.plusSeconds(1));
  assertEquals("FAILED",r.get(bad.taskId()).state());assertThrows(BusinessException.class,()->r.retry(bad.taskId(),NOW.plusSeconds(2)));
 }
 @Test void nextDateExpiresQueuedButKeepsInFlightForReconciliation(){
  var f=new TaskMysqlTest();var r=f.repo();var t=r.createComparison(snapshot(NOW),DAY,3,NOW);
  assertNull(r.claim(NOW.plus(Duration.ofDays(1)),TYPES));assertEquals("COMPARISON_CYCLE_EXPIRED",r.get(t.taskId()).errorCode());
  assertTrue(r.attempts(t.taskId()).isEmpty());
  var next=NOW.plus(Duration.ofDays(1));var s=new ComparisonTaskRecords.Snapshot("SH600030",UUID.randomUUID(),"a".repeat(64),DAY.plusDays(1).minusYears(1),DAY.plusDays(1),"baostock-comparison-backward-close-v1",next);
  var live=r.createComparison(s,DAY.plusDays(1),3,next);var c=r.claim(next,TYPES);
  assertNull(r.claim(next.plus(Duration.ofDays(1)),TYPES));assertEquals("RECONCILING",r.get(live.taskId()).state());
  assertEquals(1,r.pendingReceipts(TYPES).size());assertThrows(BusinessException.class,()->r.retry(t.taskId(),next));
 }
 @Test void disabledTypeIsNotExpiredOrConsumedByOldWorker(){
  var f=new TaskMysqlTest();var r=f.repo();var t=r.createComparison(snapshot(NOW),DAY,3,NOW);r.claim(NOW,TYPES);
  assertNull(r.claim(NOW.plusSeconds(91),Set.of("MARKET_REFRESH","PREDICTION_VERIFY")));
  assertEquals("RUNNING",r.get(t.taskId()).state());assertTrue(r.pendingReceipts().isEmpty());
  assertNull(r.claim(NOW,Set.of()));assertThrows(BusinessException.class,()->r.claim(NOW,Set.of("unknown")));
 }
 @Test void contextFailureRollsBackParentTaskAndPayloadCheckAllowsOnlyCorrectType(){
  var f=new TaskMysqlTest();var r=f.repo();
  f.jdbc.execute("CREATE TRIGGER reject_comparison_context BEFORE INSERT ON comparison_update_context FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture reject'");
  try{
   assertThrows(org.springframework.dao.DataAccessException.class,()->r.createComparison(snapshot(NOW),DAY,3,NOW));
   assertTrue(r.list().isEmpty());assertEquals(0,f.jdbc.queryForObject("select count(*) from comparison_update_context",Integer.class));
  }finally{f.jdbc.execute("DROP TRIGGER reject_comparison_context");}
  var t=r.createComparison(snapshot(NOW),DAY,3,NOW);
  assertThrows(org.springframework.dao.DataAccessException.class,()->f.jdbc.update("update scheduled_task set prediction_id=? where task_id=?",UUID.randomUUID().toString(),t.taskId()));
 }

 @Test void moreThanHundredExpiredCyclesCannotLeakIntoClaim(){
  var f=new TaskMysqlTest();var r=f.repo();
  for(int i=1;i<=101;i++){
   var cycle=DAY.minusDays(i);var asOf=cycle.atTime(20,5).atZone(ZoneId.of("Asia/Shanghai")).toInstant();
   var s=new ComparisonTaskRecords.Snapshot("SH600030",UUID.randomUUID(),"a".repeat(64),cycle.minusYears(1),cycle,
    "baostock-comparison-backward-close-v1",asOf);
   r.createComparison(s,cycle,3,asOf);
  }
  var current=r.createComparison(snapshot(NOW),DAY,3,NOW);
  var claim=r.claim(NOW,TYPES);assertNotNull(claim);assertEquals(current.taskId(),claim.taskId());
  assertEquals(0,f.jdbc.queryForObject("select count(*) from task_attempt a join comparison_update_context c on c.task_id=a.task_id where c.cycle_date<?",Integer.class,DAY));
 }
}
