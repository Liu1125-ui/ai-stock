package com.aistock.scheduler.alert;
import static com.aistock.scheduler.alert.AlertJobRecords.*;
import java.time.Clock;import org.springframework.beans.factory.annotation.Value;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.stereotype.Component;
@Component public class AlertJobWorker {
 private final AlertJobRepository jobs;private final AlertTaskClient client;private final Clock clock;private final String token;private final boolean enabled;
 public AlertJobWorker(AlertJobRepository jobs,AlertTaskClient client,Clock clock,@Value("${aistock.internal.token:}")String token,@Value("${aistock.alert.worker-enabled:false}")boolean enabled){this.jobs=jobs;this.client=client;this.clock=clock;this.token=token;this.enabled=enabled;}
 @Scheduled(fixedDelayString="${aistock.alert.poll-ms:1000}") public synchronized void tick(){if(enabled&&!token.isBlank())runOnce();}
 public synchronized void runOnce(){for(var c:jobs.pendingReceipts())recover(c);var c=jobs.claim(clock.instant());if(c==null)return;try{Result result=switch(c.stage()){case "PREPARE_EVIDENCE"->client.prepare(c);case "EVALUATE_RULE"->client.evaluate(c);default->throw new IllegalStateException("Unsupported alert phase");};if(result==null||"RUNNING".equals(result.state())||!jobs.finish(c,result))jobs.unknown(c);}catch(Exception unknown){jobs.unknown(c);}}
 private void recover(Claim c){try{var r=client.receipt(c);if(r==null||"RUNNING".equals(r.state())||!jobs.reconcile(c,r))jobs.unknown(c);}catch(AlertTaskClient.MissingReceipt missing){if(!clock.instant().isBefore(c.leaseUntil()))jobs.reconcile(c,new Result(c.attemptId(),"FAILED",null,"ATTEMPT_EXPIRED"));else jobs.unknown(c);}catch(Exception unavailable){jobs.unknown(c);}}
}
