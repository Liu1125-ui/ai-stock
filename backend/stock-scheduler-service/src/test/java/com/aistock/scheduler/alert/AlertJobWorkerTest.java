package com.aistock.scheduler.alert;
import static com.aistock.scheduler.alert.AlertJobRecords.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;import org.junit.jupiter.api.*;
@Tag("mysql") class AlertJobWorkerTest {
 AlertJobMysqlTest fixture;AlertJobRepository jobs;AlertJobMysqlTest.TestClock clock;Fake client;AlertJobWorker worker;
 @BeforeEach void setup(){fixture=new AlertJobMysqlTest();fixture.setup();jobs=fixture.jobs;clock=fixture.clock;client=new Fake();worker=new AlertJobWorker(jobs,client,clock,"local-test",false);}
 @Test void executesTwoStagesWithDistinctOperations(){var j=fixture.enqueue();worker.runOnce();assertEquals("EVALUATE_RULE",jobs.get(j.id()).stage());worker.runOnce();assertEquals("SUCCEEDED",jobs.get(j.id()).state());assertEquals(1,client.prepares);assertEquals(1,client.evaluates);assertNotEquals(client.prepareId,client.evalId);}
 @Test void timeoutRecoversOriginalRemoteCommitAfterWorkerRestart(){var j=fixture.enqueue();client.unknown=true;worker.runOnce();assertEquals("RECONCILING",jobs.get(j.id()).state());var op=client.prepareId;clock.advance(10);client.receipt=new Result(op,"AVAILABLE",client.evidence,null);new AlertJobWorker(jobs,client,clock,"test",false).runOnce();assertEquals("SUCCEEDED",jobs.get(j.id()).state());assertEquals(op,client.readId);assertEquals(1,client.prepares);}
 @Test void missingReceiptBeforeExpiryCannotRedispatchAndAfterExpiryAllowsBackoff(){var j=fixture.enqueue();client.unknown=true;client.missing=true;worker.runOnce();clock.advance(10);worker.runOnce();assertEquals("RECONCILING",jobs.get(j.id()).state());assertEquals(1,client.prepares);clock.advance(111);worker.runOnce();assertEquals("RETRY_WAIT",jobs.get(j.id()).state());assertEquals("ATTEMPT_EXPIRED",jobs.get(j.id()).errorCode());assertEquals(1,client.prepares);clock.advance(60);client.unknown=false;worker.runOnce();assertEquals(2,client.prepares);assertEquals("EVALUATE_RULE",jobs.get(j.id()).stage());}
 @Test void unavailableReceiptEvenAfterLeaseNeverMeansMissing(){var j=fixture.enqueue();client.unknown=true;worker.runOnce();clock.advance(121);worker.runOnce();assertEquals("RECONCILING",jobs.get(j.id()).state());assertEquals(1,client.prepares);}
 @Test void directLateResponseMustBeReconciled(){var j=fixture.enqueue();client.late=true;worker.runOnce();assertEquals("RECONCILING",jobs.get(j.id()).state());clock.advance(10);client.receipt=new Result(client.prepareId,"AVAILABLE",client.evidence,null);worker.runOnce();assertEquals("SUCCEEDED",jobs.get(j.id()).state());}
 @Test void disabledOrUnconfiguredTickDoesNotDispatch(){fixture.enqueue();worker.tick();new AlertJobWorker(jobs,client,clock,"",true).tick();assertEquals(0,client.prepares);}
 class Fake implements AlertTaskClient {int prepares,evaluates;UUID prepareId,evalId,readId,evidence=UUID.randomUUID();boolean unknown,missing,late;Result receipt;
  public Result prepare(Claim c){prepares++;prepareId=c.attemptId();if(unknown)throw new IllegalStateException("network outcome unknown");if(late)clock.advance(121);return new Result(c.attemptId(),"AVAILABLE",evidence,null);}
  public Result evaluate(Claim c){evaluates++;evalId=c.attemptId();return new Result(c.attemptId(),"NOT_MET",null,null);}
  public Result receipt(Claim c){readId=c.attemptId();if(missing)throw new MissingReceipt();if(receipt==null)throw new IllegalStateException("dependency down");return receipt;}
 }
}
