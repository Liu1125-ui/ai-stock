package com.aistock.notification.alertmail;

import static com.aistock.notification.alertmail.AlertMailRecords.*;
import java.sql.*;import java.time.*;import java.util.*;import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;import org.springframework.transaction.support.*;

/** Owns only the notification database; owner guard always precedes delivery and rule locks. */
public final class AlertMailRepository {
 final JdbcTemplate jdbc;private final TransactionTemplate tx;
 public AlertMailRepository(JdbcTemplate jdbc){this.jdbc=jdbc;tx=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);tx.setTimeout(5);}
 public <T>T locked(UUID owner,Supplier<T> action){return tx.execute(s->{jdbc.update("insert into alert_user_guard(user_id) values(?) on duplicate key update user_id=values(user_id)",owner.toString());jdbc.queryForMap("select user_id from alert_user_guard where user_id=? for update",owner.toString());return action.get();});}
 static void mandatory(){if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Alert mail requires existing transaction");}
 public Delivery find(UUID id){var rows=jdbc.query("select * from alert_mail_delivery where delivery_id=?",AlertMailRepository::map,id.toString());return rows.isEmpty()?null:rows.get(0);}
 Delivery lock(UUID id){mandatory();var rows=jdbc.query("select * from alert_mail_delivery where delivery_id=? for update",AlertMailRepository::map,id.toString());return rows.isEmpty()?null:rows.get(0);}
 /** Bounded housekeeping without claiming work or extending any deadline. */
 public int recoverExpired(Instant now){
  var candidates=jdbc.query("select * from alert_mail_delivery where (state='SENDING' and lease_until<=?) or (state in('PENDING','RETRY_WAIT') and expires_at<=?) or (state='CHECKING' and lease_until<=? and expires_at<=?) order by created_at,delivery_id limit 100",AlertMailRepository::map,stamp(now),stamp(now),stamp(now),stamp(now));
  int changed=0;
  for(var candidate:candidates){
   changed+=locked(candidate.ownerId(),()->{
    var row=lock(candidate.deliveryId());if(row==null)return 0;
    if("SENDING".equals(row.state())&&!row.leaseUntil().isAfter(now)){
     terminal(row,"UNKNOWN","SENDING_LEASE_EXPIRED",now);return 1;
    }
    boolean waiting=Set.of("PENDING","RETRY_WAIT").contains(row.state());
    boolean abandoned="CHECKING".equals(row.state())&&!row.leaseUntil().isAfter(now);
    if((waiting||abandoned)&&!now.isBefore(row.expiresAt())){
     terminal(row,"EXPIRED","DELIVERY_EXPIRED",now);return 1;
    }
    return 0;
   });
  }
  return changed;
 }
 public Delivery claim(Instant now,Duration lease){
  if(lease.getSeconds()<30||lease.getSeconds()>300)throw new IllegalArgumentException("Invalid mail lease");
  var rows=jdbc.query("select * from alert_mail_delivery where (state in('PENDING','RETRY_WAIT') and next_attempt_at<=?) or (state in('CHECKING','SENDING') and lease_until<=?) order by next_attempt_at,created_at,delivery_id limit 1",AlertMailRepository::map,stamp(now),stamp(now));
  if(rows.isEmpty())return null;var candidate=rows.get(0);
  return locked(candidate.ownerId(),()->{
   var row=lock(candidate.deliveryId());if(row==null)return null;
   if(Set.of("CHECKING","SENDING").contains(row.state())){
    if(row.leaseUntil().isAfter(now))return null;
    if("SENDING".equals(row.state())){terminal(row,"UNKNOWN","SENDING_LEASE_EXPIRED",now);return null;}
   }else if(!Set.of("PENDING","RETRY_WAIT").contains(row.state()))return null;
   // Recheck the retry deadline after waiting for the owner guard.
   if(Set.of("PENDING","RETRY_WAIT").contains(row.state())&&jdbc.queryForObject("select next_attempt_at>? from alert_mail_delivery where delivery_id=?",Boolean.class,stamp(now),row.deliveryId().toString()))return null;
   if(!now.isBefore(row.expiresAt())){terminal(row,"EXPIRED","DELIVERY_EXPIRED",now);return null;}
   var token=UUID.randomUUID();jdbc.update("update alert_mail_delivery set state='CHECKING',reason_code=null,lease_token=?,lease_until=?,updated_at=? where delivery_id=?",token.toString(),stamp(now.plus(lease)),stamp(now),row.deliveryId().toString());return find(row.deliveryId());
  });
 }
 void terminal(Delivery row,String state,String reason,Instant now){mandatory();jdbc.update("update alert_mail_delivery set state=?,reason_code=?,lease_token=null,lease_until=null,updated_at=?,sent_at=? where delivery_id=?",state,reason,stamp(now),"SENT".equals(state)?stamp(now):null,row.deliveryId().toString());}
 static Timestamp stamp(Instant value){return Timestamp.from(value);}
 static Delivery map(ResultSet r,int n)throws SQLException{return new Delivery(UUID.fromString(r.getString("delivery_id")),UUID.fromString(r.getString("trigger_id")),UUID.fromString(r.getString("owner_id")),UUID.fromString(r.getString("rule_id")),r.getLong("rule_revision"),r.getLong("preference_revision"),r.getLong("account_revision"),r.getString("state"),r.getInt("attempts"),r.getInt("check_failures"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("expires_at").toInstant(),r.getTimestamp("updated_at").toInstant(),r.getString("lease_token")==null?null:UUID.fromString(r.getString("lease_token")),r.getTimestamp("lease_until")==null?null:r.getTimestamp("lease_until").toInstant(),r.getTimestamp("sent_at")==null?null:r.getTimestamp("sent_at").toInstant(),r.getString("reason_code"));}
}
