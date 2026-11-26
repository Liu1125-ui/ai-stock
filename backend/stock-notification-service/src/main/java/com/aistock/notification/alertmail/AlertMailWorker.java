package com.aistock.notification.alertmail;

import static com.aistock.notification.alertmail.AlertMailRecords.*;
import static com.aistock.notification.alertmail.AlertMailRepository.stamp;
import com.aistock.common.BusinessException;import com.aistock.notification.alert.*;import com.fasterxml.jackson.databind.*;
import java.time.*;import java.util.*;

public final class AlertMailWorker {
 private final AlertMailRepository repo;private final AlertMailAccountClient accounts;private final AlertMailCodec codec;private final AlertMailTransport transport;private final Clock clock;private final boolean enabled;private final int max;private final Duration lease;private final long retryInitial,retryLater;
 private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
 public AlertMailWorker(AlertMailRepository repo,AlertMailAccountClient accounts,AlertMailCodec codec,AlertMailTransport transport,Clock clock,boolean enabled,int max,Duration lease){this(repo,accounts,codec,transport,clock,enabled,max,lease,60,300);}
 public AlertMailWorker(AlertMailRepository repo,AlertMailAccountClient accounts,AlertMailCodec codec,AlertMailTransport transport,Clock clock,boolean enabled,int max,Duration lease,long retryInitial,long retryLater){if(retryInitial<1||retryLater<retryInitial||retryLater>86400||max<1||max>10||lease.getSeconds()<60||lease.getSeconds()>300)throw new IllegalArgumentException("Invalid alert mail policy");this.repo=repo;this.accounts=accounts;this.codec=codec;this.transport=transport;this.clock=clock;this.enabled=enabled;this.max=max;this.lease=lease;this.retryInitial=retryInitial;this.retryLater=retryLater;}
 public int runOnce(Instant now){
  // Maintenance never contacts account or SMTP services, even when sending is disabled.
  repo.recoverExpired(now);
  if(!enabled||!transport.ready())return 0;var claim=repo.claim(now,lease);if(claim==null)return 0;
  AlertMailAccountClient.MailTarget target;
  try{target=accounts.target(claim.ownerId(),claim.accountRevision());}
  catch(BusinessException e){change(claim,clock.instant(),row->{if("ALERT_RECIPIENT_CHANGED".equals(e.code()))repo.terminal(row,"CANCELLED","RECIPIENT_CHANGED",clock.instant());else retry(row,true,"ACCOUNT_UNAVAILABLE",clock.instant());});return 1;}
  catch(RuntimeException unavailable){change(claim,clock.instant(),row->retry(row,true,"ACCOUNT_UNAVAILABLE",clock.instant()));return 1;}
  AlertMailTransport.Command command;
  try{command=prepare(claim,target,clock.instant());}catch(AlertMailPayloadException invalid){change(claim,clock.instant(),row->repo.terminal(row,"FAILED","PAYLOAD_INVALID",clock.instant()));return 1;}
  if(command==null)return 1;AlertMailTransport.Result outcome;try{outcome=transport.send(command);}catch(RuntimeException unknown){outcome=AlertMailTransport.Result.UNKNOWN;}final var result=outcome;
  // Do not catch DB failure here: persisted SENDING is recovered as UNKNOWN, never as a fresh send.
  repo.locked(claim.ownerId(),()->{var row=repo.lock(claim.deliveryId());if(!owned(row,claim,"SENDING"))return null;var at=clock.instant();if(!at.isBefore(row.leaseUntil()))repo.terminal(row,"UNKNOWN","SENDING_LEASE_EXPIRED",at);else if(result==AlertMailTransport.Result.ACCEPTED)repo.terminal(row,"SENT",null,at);else if(result==AlertMailTransport.Result.DEFINITE_NOT_ACCEPTED)retry(row,false,"SMTP_NOT_ACCEPTED",at);else repo.terminal(row,"UNKNOWN","SMTP_RESULT_UNKNOWN",at);return null;});return 1;
 }
 private AlertMailTransport.Command prepare(Delivery claim,AlertMailAccountClient.MailTarget target,Instant now){return repo.locked(claim.ownerId(),()->{
  var row=repo.lock(claim.deliveryId());if(!owned(row,claim,"CHECKING")||!now.isBefore(row.leaseUntil()))return null;
  if(!now.isBefore(row.expiresAt())){repo.terminal(row,"EXPIRED","DELIVERY_EXPIRED",now);return null;}
  if(target==null||!row.ownerId().equals(target.userId())||target.revision()!=row.accountRevision()||!"ENABLED".equals(target.status())||!target.emailVerified()){repo.terminal(row,"CANCELLED","RECIPIENT_CHANGED",now);return null;}
  if(target.checkedAt()==null||target.checkedAt().isBefore(now.minusSeconds(5))||target.checkedAt().isAfter(now.plusSeconds(5))){retry(row,true,"ACCOUNT_UNAVAILABLE",now);return null;}
  var rules=repo.jdbc.queryForList("select enabled,revision from alert_rule where rule_id=? and user_id=? for update",row.ruleId().toString(),row.ownerId().toString());
  var prefs=repo.jdbc.queryForList("select email_enabled,revision from notification_preference where user_id=?",row.ownerId().toString());
  if(rules.isEmpty()||!Boolean.TRUE.equals(rules.get(0).get("enabled"))||((Number)rules.get(0).get("revision")).longValue()!=row.ruleRevision()){repo.terminal(row,"CANCELLED","RULE_CHANGED",now);return null;}
  if(prefs.isEmpty()||!Boolean.TRUE.equals(prefs.get(0).get("email_enabled"))||((Number)prefs.get(0).get("revision")).longValue()!=row.preferenceRevision()){repo.terminal(row,"CANCELLED","PREFERENCE_CHANGED",now);return null;}
  if(row.attempts()>=max){repo.terminal(row,"FAILED","ATTEMPTS_EXHAUSTED",now);return null;}
  var saved=repo.jdbc.queryForMap("select payload_ciphertext,payload_nonce,payload_key_id,recipient_digest from alert_mail_delivery where delivery_id=?",row.deliveryId().toString());
  String digest=codec.digest(target.email());AlertMailCodec.Payload payload;
  if(saved.get("payload_ciphertext")!=null){if(!digest.equals(saved.get("recipient_digest"))){repo.terminal(row,"CANCELLED","RECIPIENT_CHANGED",now);return null;}payload=codec.decrypt(row.deliveryId(),new AlertMailCodec.Encrypted((byte[])saved.get("payload_ciphertext"),(byte[])saved.get("payload_nonce"),(String)saved.get("payload_key_id")));if(!payload.recipient().equals(target.email()))throw new AlertMailPayloadException();}
  else{
   String json=repo.jdbc.queryForObject("select snapshot_json from alert_trigger where trigger_id=? and owner_id=?",String.class,row.triggerId().toString(),row.ownerId().toString());if(json==null||json.length()>65536)throw new AlertMailPayloadException();
   AlertEvaluationRecords.Snapshot snapshot;try{snapshot=mapper.readValue(json,AlertEvaluationRecords.Snapshot.class);}catch(Exception invalid){throw new AlertMailPayloadException();}
   if(!row.ownerId().equals(snapshot.ownerId())||!row.ruleId().equals(snapshot.ruleId())||row.ruleRevision()!=snapshot.revision())throw new AlertMailPayloadException();
   payload=new AlertMailCodec.Payload(target.email(),AlertMailTemplate.body(snapshot));var encrypted=codec.encrypt(row.deliveryId(),payload.recipient(),payload.body());repo.jdbc.update("update alert_mail_delivery set payload_ciphertext=?,payload_nonce=?,payload_key_id=?,recipient_digest=? where delivery_id=?",encrypted.ciphertext(),encrypted.nonce(),encrypted.keyId(),digest,row.deliveryId().toString());
  }
  repo.jdbc.update("update alert_mail_delivery set state='SENDING',attempts=attempts+1,updated_at=? where delivery_id=?",stamp(now),row.deliveryId().toString());return new AlertMailTransport.Command(payload.recipient(),"股票时序分析系统：日线预警",payload.body());
 });}
 private void change(Delivery claim,Instant now,java.util.function.Consumer<Delivery> action){repo.locked(claim.ownerId(),()->{var row=repo.lock(claim.deliveryId());if(owned(row,claim,"CHECKING")&&now.isBefore(row.leaseUntil())){if(!now.isBefore(row.expiresAt()))repo.terminal(row,"EXPIRED","DELIVERY_EXPIRED",now);else action.accept(row);}return null;});}
 private static boolean owned(Delivery row,Delivery claim,String state){return row!=null&&state.equals(row.state())&&Objects.equals(row.leaseToken(),claim.leaseToken());}
 private void retry(Delivery row,boolean checking,String reason,Instant now){int count=checking?row.checkFailures()+1:row.attempts();if(checking)repo.jdbc.update("update alert_mail_delivery set check_failures=? where delivery_id=?",count,row.deliveryId().toString());if(!now.isBefore(row.expiresAt()))repo.terminal(row,"EXPIRED","DELIVERY_EXPIRED",now);else if(count>=max)repo.terminal(row,"FAILED",reason,now);else repo.jdbc.update("update alert_mail_delivery set state='RETRY_WAIT',reason_code=?,lease_token=null,lease_until=null,next_attempt_at=?,updated_at=? where delivery_id=?",reason,stamp(now.plusSeconds(count<=1?retryInitial:retryLater)),stamp(now),row.deliveryId().toString());}
}
