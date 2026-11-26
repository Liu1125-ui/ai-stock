package com.aistock.scheduler.admin;
import com.aistock.common.BusinessException;
import com.aistock.scheduler.task.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.time.*;import java.util.*;import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.aistock.scheduler.admin.AdminTaskActionRecords.*;

@Tag("mysql")
class AdminTaskActionMysqlTest {
 final Instant now=Instant.parse("2026-10-20T12:00:00Z");
 final LocalDate day=LocalDate.of(2026,10,20);
 final UUID actor=UUID.fromString("00000000-0000-0000-0000-000000000001");
 JdbcTemplate jdbc;TaskRepository worker;AdminTaskActionService service;
 @BeforeEach void init() {
  var url=System.getenv("SCHEDULER_TEST_DB_URL");var secret=System.getenv("SCHEDULER_TEST_DB_PASSWORD");
  if(!"jdbc:mysql://127.0.0.1:13316/stock_scheduler_test?serverTimezone=UTC".equals(url)||secret==null||secret.isBlank())throw new IllegalStateException("isolated test db required");
  var ds=new DriverManagerDataSource(url,"root",secret);ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
  Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();jdbc=new JdbcTemplate(ds);
  assertEquals("stock_scheduler_test",jdbc.queryForObject("select database()",String.class));
  jdbc.update("delete from admin_task_operation");jdbc.update("delete from comparison_update_context");jdbc.update("delete from task_attempt");jdbc.update("delete from admin_market_refresh_operation" );jdbc.update("delete from admin_market_refresh_guard" );jdbc.update("delete from admin_market_refresh_context" );jdbc.update("delete from scheduled_task");
  worker=new TaskRepository(jdbc);service=service(true,true);
 }
 AdminTaskActionService service(boolean enabled,boolean workerEnabled){return new AdminTaskActionService(new AdminTaskActionRepository(jdbc,new AdminTaskActionPolicy(),enabled,60,workerEnabled,true));}
 UUID queued(){return UUID.fromString(worker.create("SH600030",day,"test-"+UUID.randomUUID(),now).taskId());}
 long revision(UUID id){return jdbc.queryForObject("select revision from scheduled_task where task_id=?",Long.class,id.toString());}
 int operations(){return jdbc.queryForObject("select count(*) from admin_task_operation",Integer.class);}
 Command command(UUID task,Action action){return new Command(UUID.randomUUID(),Long.toString(revision(task)),action==Action.RETRY?"RETRY_TRANSIENT_FAILURE":"CANCEL_NOT_NEEDED");}
 Instant failRound(UUID id,Instant clock){for(int n=0;n<3;n++){var c=worker.claim(clock);assertEquals(id.toString(),c.taskId());worker.finish(c,new MarketRefreshClient.Result(c.attemptId(),"FAILED",null,"SOURCE_TIMEOUT"),clock);clock=clock.plusSeconds(n==0?60:120);}return clock;}
 @Test void retryAndSameIdReplayRetainHistoryAndSubmittedSnapshot(){
  var id=queued();var time=failRound(id,now);var c=command(id,Action.RETRY);
  var first=service.execute(actor,id,Action.RETRY,c,time);assertEquals(200,first.status());
  assertEquals(first,service.execute(actor,id,Action.RETRY,c,time.plusSeconds(1)));
  assertEquals(1,operations());assertEquals(0,worker.get(id.toString()).attemptCount());assertEquals(3,worker.attempts(id.toString()).size());
  worker.claim(time);assertEquals("QUEUED",service.receipt(actor,id,c.operationId()).afterState());
  assertEquals("RUNNING",worker.get(id.toString()).state());
 }
 @Test void unknownNotDeliveredRequestCanBeManuallyRecoveredWithOriginalId(){
  var id=queued();var c=command(id,Action.CANCEL);
  assertEquals("OPERATION_NOT_FOUND",assertThrows(BusinessException.class,()->service.receipt(actor,id,c.operationId())).code());
  assertEquals(200,service.execute(actor,id,Action.CANCEL,c,now).status());
  assertEquals(200,service.execute(actor,id,Action.CANCEL,c,now.plusSeconds(1)).status());assertEquals(1,revision(id));
 }
 @Test void replayKeyCannotChangeActorTaskOrContents(){
  var id=queued();var c=command(id,Action.CANCEL);service.execute(actor,id,Action.CANCEL,c,now);
  assertEquals("OPERATION_KEY_CONFLICT",assertThrows(BusinessException.class,()->service.execute(UUID.randomUUID(),id,Action.CANCEL,c,now)).code());
  var changed=new Command(c.operationId(),c.expectedRevision(),"CANCEL_DUPLICATE_REQUEST");
  assertThrows(BusinessException.class,()->service.execute(actor,id,Action.CANCEL,changed,now));
  assertThrows(BusinessException.class,()->service.execute(actor,queued(),Action.CANCEL,c,now));
  assertEquals("OPERATION_NOT_FOUND",assertThrows(BusinessException.class,()->service.receipt(UUID.randomUUID(),id,c.operationId())).code());
 }
 @Test void concurrentIdenticalCommandsHaveOneStoredResult()throws Exception {
  var id=queued();var c=command(id,Action.CANCEL);var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
  try{Callable<CommandResult> call=()->{start.await();return service.execute(actor,id,Action.CANCEL,c,now);};var a=pool.submit(call);var b=pool.submit(call);start.countDown();assertEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));assertEquals(1,operations());assertEquals(1,revision(id));}finally{pool.shutdownNow();}
 }
 @Test void concurrentDifferentCommandsOnSameRevisionApplyAtMostOnce()throws Exception {
  var id=queued();var a=command(id,Action.CANCEL);var b=command(id,Action.CANCEL);var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
  try{var f=pool.submit(()->{start.await();return service.execute(actor,id,Action.CANCEL,a,now);});var g=pool.submit(()->{start.await();return service.execute(UUID.randomUUID(),id,Action.CANCEL,b,now);});start.countDown();var results=List.of(f.get(10,TimeUnit.SECONDS),g.get(10,TimeUnit.SECONDS));assertEquals(1,results.stream().filter(r->r.status()==200).count());assertEquals(1,revision(id));assertEquals(2,operations());}finally{pool.shutdownNow();}
 }
 @Test void claimFirstRejectsCancelAndCancelFirstPreventsClaim(){
  var id=queued();var c=command(id,Action.CANCEL);worker.claim(now);
  assertEquals("TASK_REVISION_CONFLICT",service.execute(actor,id,Action.CANCEL,c,now).code());
  var second=queued();assertEquals(200,service.execute(actor,second,Action.CANCEL,command(second,Action.CANCEL),now).status());assertNull(worker.claim(now));
 }
 @Test void workerHoldingTheTaskLockWinsAgainstAnAdministratorCancel()throws Exception {
  var id=queued();var command=command(id,Action.CANCEL);
  var claimed=new CountDownLatch(1);var release=new CountDownLatch(1);
  var pool=Executors.newFixedThreadPool(2);
  var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
  try {
   var running=pool.submit(()->tx.execute(status->{
    var result=worker.claim(now);claimed.countDown();
    try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("test barrier timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
    return result;
   }));
   assertTrue(claimed.await(5,TimeUnit.SECONDS));
   var cancel=pool.submit(()->service.execute(actor,id,Action.CANCEL,command,now));
   release.countDown();assertNotNull(running.get(10,TimeUnit.SECONDS));
   assertEquals("TASK_REVISION_CONFLICT",cancel.get(10,TimeUnit.SECONDS).code());
   assertEquals("RUNNING",worker.get(id.toString()).state());
  }finally{release.countDown();pool.shutdownNow();}
 }
 @Test void rejectedOperationStaysRejectedWhenConditionsLaterChange(){
  var id=queued();var c=command(id,Action.RETRY);var first=service.execute(actor,id,Action.RETRY,c,now);
  assertEquals(409,first.status());failRound(id,now);
  assertEquals(first,service.execute(actor,id,Action.RETRY,c,now.plusSeconds(400)));assertEquals("FAILED",worker.get(id.toString()).state());
 }
 @Test void failureDuringAuditUpdateRollsBackTaskAndPendingRow(){
  var id=queued();var c=command(id,Action.CANCEL);
  jdbc.execute("CREATE TRIGGER reject_action_update BEFORE UPDATE ON admin_task_operation FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture reject'");
  try{assertThrows(BusinessException.class,()->service.execute(actor,id,Action.CANCEL,c,now));assertEquals("QUEUED",worker.get(id.toString()).state());assertEquals(0,revision(id));assertEquals(0,operations());}finally{jdbc.execute("DROP TRIGGER reject_action_update");}
 }
 @Test void missingTaskRollsBackRegistrationAndGlobalOffPreservesReads(){
  var absent=UUID.randomUUID();assertThrows(BusinessException.class,()->service.execute(actor,absent,Action.CANCEL,new Command(UUID.randomUUID(),"0","CANCEL_NOT_NEEDED"),now));assertEquals(0,operations());
  var id=queued();var closed=service(false,true);assertFalse(closed.options(id,now).actionsEnabled());
  assertThrows(BusinessException.class,()->closed.execute(actor,id,Action.CANCEL,command(id,Action.CANCEL),now));
  var c=command(id,Action.CANCEL);service.execute(actor,id,Action.CANCEL,c,now);assertEquals("APPLIED",closed.receipt(actor,id,c.operationId()).outcome());
 }
 @Test void cooldownReadsLastAppliedRetryAndDisabledWorkerCannotRetry(){
  var id=queued();var at=failRound(id,now);service.execute(actor,id,Action.RETRY,command(id,Action.RETRY),at);
  assertFalse(service(true,false).options(id,at).retry().allowed());
  // A fixed failed fixture isolates the manual cooldown from automatic 60/120 second backoff.
  jdbc.update("update scheduled_task set state='FAILED',error_code='SOURCE_TIMEOUT',revision=revision+1 where task_id=?",id.toString());
  assertEquals("TASK_RETRY_COOLDOWN",service.options(id,at.plusSeconds(59)).retry().reasonCode());
  assertTrue(service.options(id,at.plusSeconds(60)).retry().allowed());
 }
 @Test void comparisonDoesNotResetBudgetOrPermitNextDay(){
  var s=new ComparisonTaskRecords.Snapshot("SH600030",UUID.randomUUID(),"a".repeat(64),day.minusYears(1),day,"baostock-comparison-backward-close-v1",now);
  var id=UUID.fromString(worker.createComparison(s,day,3,now).taskId());var claim=worker.claim(now,Set.of("COMPARISON_UPDATE"));
  worker.finish(claim,new MarketRefreshClient.Result(claim.attemptId(),"FAILED",null,"SOURCE_TIMEOUT"),now);
  jdbc.update("update scheduled_task set state='FAILED',error_code='SOURCE_TIMEOUT',revision=revision+1 where task_id=?",id.toString());
  assertEquals(200,service.execute(actor,id,Action.RETRY,command(id,Action.RETRY),now).status());assertEquals(1,worker.get(id.toString()).attemptCount());
  jdbc.update("update scheduled_task set state='FAILED',error_code='SOURCE_TIMEOUT',revision=revision+1 where task_id=?",id.toString());
  assertEquals("COMPARISON_CYCLE_EXPIRED",service.execute(actor,id,Action.RETRY,command(id,Action.RETRY),now.plusSeconds(86400)).code());
 }
 @Test void operationVersionCannotBeBypassedAfterSameMillisecondStateCycle(){
  var id=queued();var c=command(id,Action.CANCEL);failRound(id,now);
  jdbc.update("update scheduled_task set state='QUEUED',revision=revision+1 where task_id=?",id.toString());
  assertEquals("TASK_REVISION_CONFLICT",service.execute(actor,id,Action.CANCEL,c,now).code());
 }
 @Test void optionsAndHistoryDoNotMutateAndInvalidCooldownFailsAtConstruction(){
  var id=queued();assertTrue(service.options(id,now).cancel().allowed());
  assertTrue(service.history(id,new HistoryQuery(20),null,now).items().isEmpty());
  assertEquals(0,revision(id));assertEquals(0,operations());
  assertThrows(IllegalArgumentException.class,()->new AdminTaskActionRepository(jdbc,new AdminTaskActionPolicy(),false,0,true,true));
 }
 @Test void historyPagesBeyondFiftyUseStableKeysAndActorLookupIsPrivate(){
  var id=queued();for(int i=0;i<55;i++)service.execute(actor,id,Action.RETRY,command(id,Action.RETRY),now);
  var seen=new HashSet<String>();String token=null;int pages=0;
  do{var page=service.history(id,new HistoryQuery(20),token,now);for(var item:page.items()){assertTrue(seen.add(item.receipt().operationId()));assertEquals(actor.toString(),item.actorUserId());}token=page.nextCursor();pages++;}while(token!=null);
  assertEquals(55,seen.size());assertEquals(3,pages);assertEquals(0,revision(id));
 }
}
