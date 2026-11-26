package com.aistock.notification.alertmail;
import com.aistock.notification.alert.*;import com.fasterxml.jackson.databind.*;
import java.time.*;import java.util.*;import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
@Tag("mysql") class AlertMailWorkerMysqlTest extends AlertMailDatabaseFixture {
 AlertMailAccountClient accounts;AlertMailTransport transport;AlertMailWorker worker;int calls;java.util.concurrent.atomic.AtomicReference<Instant> time;
 @BeforeEach void workerSetup()throws Exception{
  preference(true,3);var e=new ObjectMapper().findAndRegisterModules().readValue(getClass().getResourceAsStream("/alert/evidence.json"),AlertEvidenceClient.Evidence.class);
  capture(true,true);String json=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).writeValueAsString(new AlertEvaluationRecords.Snapshot(rule,owner,e.instrumentId(),0,"PRICE_ABOVE","25",e.tradeDate(),e,clock.instant()));jdbc.update("update alert_trigger set snapshot_json=? where trigger_id=?",json,trigger.toString());
  time=new java.util.concurrent.atomic.AtomicReference<>(clock.instant());var mutable=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return time.get();}};
  accounts=mock(AlertMailAccountClient.class);when(accounts.target(owner,7)).thenAnswer(c->new AlertMailAccountClient.MailTarget(owner,"ENABLED",7,true,"fixture@example.com",time.get()));
  transport=mock(AlertMailTransport.class);when(transport.ready()).thenReturn(true);doAnswer(c->{calls++;return AlertMailTransport.Result.ACCEPTED;}).when(transport).send(any());
  worker=new AlertMailWorker(repo,accounts,new AlertMailCodec(new byte[32],new byte[32]),transport,mutable,true,3,Duration.ofSeconds(120));
 }
 AlertMailRecords.Delivery delivery(){return repo.find(jdbc.queryForObject("select delivery_id from alert_mail_delivery where trigger_id=?",(r,n)->UUID.fromString(r.getString(1)),trigger.toString()));}
 @Test void acceptedIsStoredOnceAndSnapshotMailIsNotVerification(){assertEquals(1,worker.runOnce(time.get()));assertEquals("SENT",delivery().state());assertEquals(1,delivery().attempts());assertEquals(0,worker.runOnce(time.get().plusSeconds(10)));assertEquals(1,calls);}
 @Test void preferenceRevisionChangeCancelsOldMail(){preference(false,4);preference(true,5);worker.runOnce(time.get());assertEquals("CANCELLED",delivery().state());assertEquals(0,calls);assertEquals(1,count("inbox_message"));}
 @Test void disabledAccountAndRuleChangeCancelWithoutSmtp(){when(accounts.target(owner,7)).thenReturn(new AlertMailAccountClient.MailTarget(owner,"DISABLED",7,true,"fixture@example.com",time.get()));worker.runOnce(time.get());assertEquals("CANCELLED",delivery().state());assertEquals(0,calls);}
 @Test void ruleRevisionMismatchCancels(){jdbc.update("update alert_rule set revision=1 where rule_id=?",rule.toString());worker.runOnce(time.get());assertEquals("CANCELLED",delivery().state());assertEquals(0,calls);}
 @Test void changedRecipientIsTerminalAndDependencyFailureIsBounded(){when(accounts.target(owner,7)).thenThrow(new com.aistock.common.BusinessException("ALERT_DEPENDENCY_UNAVAILABLE","safe",org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));worker.runOnce(time.get());assertEquals("RETRY_WAIT",delivery().state());assertEquals(0,delivery().attempts());time.set(time.get().plusSeconds(61));worker.runOnce(time.get());time.set(time.get().plusSeconds(301));worker.runOnce(time.get());assertEquals("FAILED",delivery().state());assertEquals(3,delivery().checkFailures());assertEquals(0,calls);}
 @Test void definiteFailureRetriesButUncertainOutcomeNeverDoes(){doAnswer(c->{calls++;return AlertMailTransport.Result.DEFINITE_NOT_ACCEPTED;}).when(transport).send(any());worker.runOnce(time.get());assertEquals("RETRY_WAIT",delivery().state());time.set(time.get().plusSeconds(61));doAnswer(c->{calls++;return AlertMailTransport.Result.UNKNOWN;}).when(transport).send(any());worker.runOnce(time.get());assertEquals("UNKNOWN",delivery().state());time.set(time.get().plusSeconds(400));worker.runOnce(time.get());assertEquals(2,calls);}
 @Test void successfulSmtpThenDatabaseFailureBecomesUnknownAfterLease(){String name="fail_sent_"+owner.toString().replace("-","");jdbc.execute("CREATE TRIGGER "+name+" BEFORE UPDATE ON alert_mail_delivery FOR EACH ROW BEGIN IF NEW.owner_id='"+owner+"' AND NEW.state='SENT' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture reject receipt'; END IF; END");try{assertThrows(RuntimeException.class,()->worker.runOnce(time.get()));assertEquals("SENDING",delivery().state());assertEquals(1,calls);time.set(time.get().plusSeconds(121));worker.runOnce(time.get());assertEquals("UNKNOWN",delivery().state());assertEquals(1,calls);}finally{jdbc.execute("DROP TRIGGER "+name);}}
 @Test void malformedSnapshotCannotSend(){jdbc.update("update alert_trigger set snapshot_json='{}' where trigger_id=?",trigger.toString());worker.runOnce(time.get());assertEquals("FAILED",delivery().state());assertEquals(0,calls);}
 @Test void disabledWorkerStillRecoversExpiredSendingWithoutExternalCalls(){
  jdbc.update("update alert_mail_delivery set state='SENDING',lease_token=?,lease_until=? where trigger_id=?",UUID.randomUUID().toString(),java.sql.Timestamp.from(time.get().minusSeconds(1)),trigger.toString());
  new AlertMailWorker(repo,accounts,new AlertMailCodec(new byte[32],new byte[32]),transport,clock,false,3,Duration.ofSeconds(120)).runOnce(time.get());
  assertEquals("UNKNOWN",delivery().state());verifyNoInteractions(accounts);verify(transport,never()).send(any());
 }
 @Test void unconfiguredWorkerStillExpiresPendingWithoutExternalCalls(){
  when(transport.ready()).thenReturn(false);time.set(delivery().expiresAt().plusSeconds(1));worker.runOnce(time.get());
  assertEquals("EXPIRED",delivery().state());verifyNoInteractions(accounts);verify(transport,never()).send(any());
 }
 @Test void temporaryPrepareDatabaseFailureKeepsCheckingAndCanRecover(){
  var failing=spy(repo);var transactions=new java.util.concurrent.atomic.AtomicInteger();
  doAnswer(invocation->{if(transactions.incrementAndGet()==2)throw new org.springframework.dao.TransientDataAccessResourceException("fixture temporary transaction outage");return invocation.callRealMethod();}).when(failing).locked(any(),any());
  var first=new AlertMailWorker(failing,accounts,new AlertMailCodec(new byte[32],new byte[32]),transport,clock,true,3,Duration.ofSeconds(120));
  try{first.runOnce(time.get());}catch(org.springframework.dao.DataAccessException expected){}
  assertEquals("CHECKING",delivery().state());assertEquals(0,calls);
  time.set(time.get().plusSeconds(121));worker.runOnce(time.get());assertEquals("SENT",delivery().state());assertEquals(1,calls);
 }
 @Test void disabledWorkerExpiresPendingAndUnconfiguredWorkerRecoversSending(){
  time.set(delivery().expiresAt().plusSeconds(1));
  new AlertMailWorker(repo,accounts,new AlertMailCodec(new byte[32],new byte[32]),transport,clock,false,3,Duration.ofSeconds(120)).runOnce(time.get());
  assertEquals("EXPIRED",delivery().state());
  jdbc.update("update alert_mail_delivery set state='SENDING',lease_token=?,lease_until=? where trigger_id=?",UUID.randomUUID().toString(),java.sql.Timestamp.from(time.get().minusSeconds(1)),trigger.toString());
  when(transport.ready()).thenReturn(false);worker.runOnce(time.get());assertEquals("UNKNOWN",delivery().state());
  verifyNoInteractions(accounts);verify(transport,never()).send(any());
 }
 @Test void definiteFailureCanRecoverAndBeAcceptedExactlyOnce(){
  doReturn(AlertMailTransport.Result.DEFINITE_NOT_ACCEPTED).when(transport).send(any());worker.runOnce(time.get());assertEquals("RETRY_WAIT",delivery().state());
  time.set(time.get().plusSeconds(61));doAnswer(c->{calls++;return AlertMailTransport.Result.ACCEPTED;}).when(transport).send(any());worker.runOnce(time.get());
  assertEquals("SENT",delivery().state());worker.runOnce(time.get().plusSeconds(400));assertEquals(1,calls);assertEquals(2,delivery().attempts());
 }
}
