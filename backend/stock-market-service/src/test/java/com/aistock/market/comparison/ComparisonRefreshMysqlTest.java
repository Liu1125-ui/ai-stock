package com.aistock.market.comparison;
import com.aistock.common.BusinessException;
import com.aistock.market.support.MarketDatabaseFixture;
import com.aistock.market.version.MarketVersionRepository;
import com.fasterxml.jackson.databind.*;
import java.time.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.aistock.market.comparison.ComparisonUpdateSamples.*;
@Tag("mysql")
class ComparisonRefreshMysqlTest {
 private ComparisonRefreshRepository repo(MarketDatabaseFixture f,Clock clock){
  var market=new MarketVersionRepository(f.jdbc());var mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  return new ComparisonRefreshRepository(f.jdbc(),new ComparisonEvidenceRepository(market,mapper,clock),new ComparisonEvidenceValidator(clock),mapper,clock);
 }
 @Test void serviceReplayAndRestartLookupNeverRecollect(){
  var f=new MarketDatabaseFixture();var clock=new MutableClock(NOW.instant());var id=raw(f,"raw",START,END,"10",false,0,clock);var r=request(f,id,START,END,clock);
  var calls=new AtomicInteger();var records=repo(f,clock);
  var service=new ComparisonRefreshService(records,new ComparisonUpdateQuery(new MarketVersionRepository(f.jdbc()),validCalendar(END.minusDays(10),END,clock),clock),
   req->{calls.incrementAndGet();return new ComparisonCollector.Collected("AVAILABLE",candidate(req,"10","20",clock),null);},clock);
  var done=service.refresh(r);assertEquals("AVAILABLE",done.state());assertEquals(1,calls.get());
  clock.advance(Duration.ofSeconds(121));
  assertEquals(done,service.refresh(r));assertEquals(done,repo(f,clock).lookup(r.operationId()));assertEquals(1,calls.get());
  assertEquals(id.toString(),f.jdbc().queryForObject("select version_id from instrument_data_head",String.class));
  assertNull(f.jdbc().queryForObject("select last_error_code from instrument_data_head",String.class));
 }
 @Test void simultaneousBeginHasOneOwnerAndChangedParametersConflict()throws Exception{
  var f=new MarketDatabaseFixture();var id=raw(f,"raw",START,END,"10",false,0,NOW);var r=request(f,id,START,END,NOW);var records=repo(f,NOW);
  var pool=Executors.newFixedThreadPool(2);
  try{
   var a=pool.submit(()->records.begin(r));var b=pool.submit(()->records.begin(r));
   assertNotEquals(a.get(5,TimeUnit.SECONDS).execute(),b.get(5,TimeUnit.SECONDS).execute());
   var changed=new ComparisonUpdateRecords.Request(r.operationId(),r.instrumentId(),r.rawVersionId(),"0".repeat(64),START,END,r.definition(),r.leaseUntil());
   assertEquals("COMPARISON_OPERATION_CONFLICT",assertThrows(BusinessException.class,()->records.replay(changed)).code());
   var lease=new ComparisonUpdateRecords.Request(r.operationId(),r.instrumentId(),r.rawVersionId(),r.rawContentHash(),START,END,r.definition(),r.leaseUntil().plusMillis(1));
   assertThrows(BusinessException.class,()->records.begin(lease));
  }finally{pool.shutdownNow();}
 }
 @Test void expiredGetIsDerivedReadOnlyAndLatePublishIsBlocked(){
  var f=new MarketDatabaseFixture();var clock=new MutableClock(NOW.instant());var id=raw(f,"raw",START,END,"10",false,0,clock);var r=request(f,id,START,END,clock);var records=repo(f,clock);
  records.begin(r);clock.advance(Duration.ofSeconds(90));
  assertEquals("ATTEMPT_EXPIRED",records.lookup(r.operationId()).errorCode());
  assertEquals("RUNNING",f.jdbc().queryForObject("select state from comparison_refresh_receipt",String.class));
  assertEquals("ATTEMPT_EXPIRED",records.complete(r,candidate(r,"10","20",clock),null).errorCode());
  assertEquals(0,f.jdbc().queryForObject("select count(*) from comparison_evidence_version",Integer.class));
 }
 @Test void failedCandidatePreservesVersionsAndNoOrphanOperation(){
  var f=new MarketDatabaseFixture();var id=raw(f,"raw",START,END,"10",false,0,NOW);var r=request(f,id,START,END,NOW);var records=repo(f,NOW);
  var c=candidate(r,"10","20",NOW);
  var missing=new ComparisonEvidenceRecords.Candidate(c.operationId(),c.instrumentId(),c.rawVersionId(),c.from(),c.to(),c.provider(),
   c.definition(),c.fetchedAt(),c.sourcePayloadHash(),c.points().subList(0,c.points().size()-1),c.calendar());
  var s=new ComparisonRefreshService(records,new ComparisonUpdateQuery(new MarketVersionRepository(f.jdbc()),validCalendar(END.minusDays(10),END,NOW),NOW),
   req->new ComparisonCollector.Collected("AVAILABLE",missing,null),NOW);
  var result=s.refresh(r);assertEquals("FAILED",result.state());assertEquals("COMPARISON_TARGET_MISSING",result.errorCode());
  assertEquals(0,f.jdbc().queryForObject("select count(*) from comparison_evidence_operation",Integer.class));
  assertEquals(0,f.jdbc().queryForObject("select count(*) from comparison_evidence_version",Integer.class));
 }
 @Test void publicationLockWaitAcrossLeaseRollsBackAllNewWrites()throws Exception{
  var f=new MarketDatabaseFixture();var clock=new MutableClock(NOW.instant());var id=raw(f,"raw",START,END,"10",false,0,clock);var r=request(f,id,START,END,clock);var records=repo(f,clock);
  var original=request(f,id,START,END,clock);var existing=new ComparisonEvidenceService(new MarketVersionRepository(f.jdbc()),clock).publish(candidate(original,"10","20",clock));
  records.begin(r);var pool=Executors.newSingleThreadExecutor();
  try(var con=f.jdbc().getDataSource().getConnection()){
   con.setAutoCommit(false);
   try(var st=con.prepareStatement("select * from comparison_evidence_version where content_hash=? for update")){
    st.setString(1,existing.contentHash());st.executeQuery().close();
   }
   var future=pool.submit(()->records.complete(r,candidate(r,"10","20",clock),null));
   boolean blocked=false;for(int i=0;i<150;i++){if(f.jdbc().queryForObject("select count(*) from performance_schema.data_lock_waits",Integer.class)>0){blocked=true;break;}Thread.sleep(20);}
   assertTrue(blocked,"must observe an actual database lock wait");clock.advance(Duration.ofSeconds(91));con.commit();
   assertEquals("ATTEMPT_EXPIRED",future.get(10,TimeUnit.SECONDS).errorCode());
   assertEquals(1,f.jdbc().queryForObject("select count(*) from comparison_evidence_version",Integer.class));
   assertEquals(1,f.jdbc().queryForObject("select count(*) from comparison_evidence_operation",Integer.class));
   assertEquals("RUNNING",f.jdbc().queryForObject("select state from comparison_refresh_receipt",String.class));
  }finally{pool.shutdownNow();}
 }
 @Test void finalReceiptWriteFailureRollsBackPublication(){
  var f=new MarketDatabaseFixture();var id=raw(f,"raw",START,END,"10",false,0,NOW);var r=request(f,id,START,END,NOW);var records=repo(f,NOW);records.begin(r);
  f.jdbc().execute("CREATE TRIGGER reject_comparison_success BEFORE UPDATE ON comparison_refresh_receipt FOR EACH ROW BEGIN IF NEW.state='AVAILABLE' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture reject'; END IF; END");
  try{
   assertThrows(org.springframework.dao.DataAccessException.class,()->records.complete(r,candidate(r,"10","20",NOW),null));
   assertEquals(0,f.jdbc().queryForObject("select count(*) from comparison_evidence_version",Integer.class));
   assertEquals(0,f.jdbc().queryForObject("select count(*) from comparison_evidence_operation",Integer.class));
   assertEquals("RUNNING",f.jdbc().queryForObject("select state from comparison_refresh_receipt",String.class));
  }finally{f.jdbc().execute("DROP TRIGGER reject_comparison_success");}
 }
 @Test void stateConstraintAndUnknownReceiptAreEnforced(){
  var f=new MarketDatabaseFixture();var id=raw(f,"raw",START,END,"10",false,0,NOW);var r=request(f,id,START,END,NOW);var records=repo(f,NOW);records.begin(r);
  assertThrows(org.springframework.dao.DataAccessException.class,()->f.jdbc().update("update comparison_refresh_receipt set state='AVAILABLE'"));
  assertEquals("COMPARISON_REFRESH_NOT_FOUND",assertThrows(BusinessException.class,()->records.lookup(UUID.randomUUID())).code());
 }

 @Test void receiptReadWaitsForPublishingCommitInsteadOfReportingFalseExpiry()throws Exception{
  var f=new MarketDatabaseFixture();var clock=new MutableClock(NOW.instant());
  var id=raw(f,"commit-raw",START,END,"10",false,0,clock);var r=request(f,id,START,END,clock);var records=repo(f,clock);
  records.begin(r);
  var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(3);
  var outer=new org.springframework.transaction.support.TransactionTemplate(
   new org.springframework.jdbc.datasource.DataSourceTransactionManager(f.jdbc().getDataSource()));
  try{
   var writer=pool.submit(()->outer.execute(status->{
    var done=records.complete(r,candidate(r,"10","20",clock),null);
    org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
     new org.springframework.transaction.support.TransactionSynchronization(){
      @Override public void beforeCommit(boolean readOnly){
       entered.countDown();
       try{if(!release.await(4,TimeUnit.SECONDS))throw new IllegalStateException("Commit barrier timed out");}
       catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
      }
     });
    return done;
   }));
   assertTrue(entered.await(3,TimeUnit.SECONDS),"writer reached the real pre-commit barrier");
   clock.advance(Duration.ofSeconds(91));
   var lookup=pool.submit(()->records.lookup(r.operationId()));var replay=pool.submit(()->records.replay(r));
   assertThrows(TimeoutException.class,()->lookup.get(150,TimeUnit.MILLISECONDS),"GET must not expose a false terminal failure");
   assertThrows(TimeoutException.class,()->replay.get(150,TimeUnit.MILLISECONDS),"replay must wait for the publishing transaction");
   release.countDown();
   var saved=writer.get(5,TimeUnit.SECONDS);assertEquals("AVAILABLE",saved.state());
   assertEquals(saved,lookup.get(5,TimeUnit.SECONDS));assertEquals(saved,replay.get(5,TimeUnit.SECONDS));
   assertEquals(1,f.jdbc().queryForObject("select count(*) from comparison_evidence_version",Integer.class));
  }finally{release.countDown();pool.shutdownNow();}
 }
}
