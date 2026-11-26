package com.aistock.notification.alertmail;

import com.aistock.notification.alert.AlertMailAccountClient.AccountContext;
import java.time.*;import java.util.*;
import static com.aistock.notification.alertmail.AlertMailRecords.*;

/** Called inside the trigger/inbox transaction, never backfills historical messages. */
public final class AlertMailOutbox {
 private final AlertMailRepository repo;private final boolean enabled;private final Duration ttl;
 public AlertMailOutbox(AlertMailRepository repo,boolean enabled,Duration ttl){if(ttl==null||ttl.getSeconds()<60||ttl.getSeconds()>604800)throw new IllegalArgumentException("Invalid mail TTL");this.repo=repo;this.enabled=enabled;this.ttl=ttl;}
 public Decision capture(UUID trigger,UUID owner,UUID rule,long ruleRevision,AccountContext account,Instant createdAt){
  AlertMailRepository.mandatory();if(account==null||!owner.equals(account.userId())||account.revision()<0||!"ENABLED".equals(account.status()))throw new IllegalArgumentException("Invalid trigger account context");
  var prefs=repo.jdbc.queryForList("select email_enabled,revision from notification_preference where user_id=?",owner.toString());boolean opted=!prefs.isEmpty()&&Boolean.TRUE.equals(prefs.get(0).get("email_enabled"));long revision=prefs.isEmpty()?0:((Number)prefs.get(0).get("revision")).longValue();
  String reason=!enabled?"SYSTEM_DISABLED":!opted?"PREFERENCE_OFF":!account.emailVerified()?"EMAIL_UNVERIFIED":null;String state=reason==null?"PENDING":"NOT_REQUESTED";var id=UUID.randomUUID();
  repo.jdbc.update("insert into alert_mail_delivery(delivery_id,trigger_id,owner_id,rule_id,rule_revision,preference_revision,account_revision,state,reason_code,created_at,expires_at,updated_at,next_attempt_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?)",id.toString(),trigger.toString(),owner.toString(),rule.toString(),ruleRevision,revision,account.revision(),state,reason,AlertMailRepository.stamp(createdAt),AlertMailRepository.stamp(createdAt.plus(ttl)),AlertMailRepository.stamp(createdAt),AlertMailRepository.stamp(createdAt));return new Decision(id,state,reason);
 }
}
