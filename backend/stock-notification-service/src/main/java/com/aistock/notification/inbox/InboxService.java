package com.aistock.notification.inbox;
import com.aistock.common.BusinessException;
import com.aistock.notification.alert.AlertEvaluationRecords.Snapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;import java.time.*;import java.util.*;
import org.springframework.stereotype.Service;import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;
import static com.aistock.notification.inbox.InboxRecords.*;
@Service public class InboxService {
 private final JdbcTemplate db;private final ObjectMapper mapper;private final Clock clock;private final TransactionTemplate tx;
 private static final String SELECT="select m.message_id,m.trigger_id,m.created_at,m.read_at,t.snapshot_json from inbox_message m join alert_trigger t on t.trigger_id=m.trigger_id and t.owner_id=m.owner_id ";
 public InboxService(JdbcTemplate db,ObjectMapper mapper,Clock clock){this.db=db;this.mapper=mapper;this.clock=clock;tx=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(db.getDataSource())));tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);tx.setTimeout(5);}
 public Page list(UUID owner,int page,int size,boolean unreadOnly){validatePage(page,size);return tx.execute(s->{String where=" where m.owner_id=?"+(unreadOnly?" and m.read_at is null":"");long total=db.queryForObject("select count(*) from inbox_message m"+where,Long.class,owner.toString());long unread=count(owner);var rows=db.query(SELECT+where+" order by m.created_at desc,m.message_id desc limit ? offset ?",(r,n)->row(r),owner.toString(),size,(long)(page-1)*size);return new Page(rows,page,size,total,unread);});}
 public Unread unreadCount(UUID owner){return new Unread(count(owner));}
 private long count(UUID owner){return db.queryForObject("select count(*) from inbox_message where owner_id=? and read_at is null",Long.class,owner.toString());}
 public TriggerPage triggers(UUID owner,UUID rule,int page,int size){validatePage(page,size);return tx.execute(s->{if(db.queryForObject("select count(*) from alert_rule where user_id=? and rule_id=?",Long.class,owner.toString(),rule.toString())!=1)throw missing();long total=db.queryForObject("select count(*) from alert_trigger where owner_id=? and rule_id=?",Long.class,owner.toString(),rule.toString());var rows=db.query(SELECT+" where m.owner_id=? and t.rule_id=? order by m.created_at desc,m.message_id desc limit ? offset ?",(r,n)->row(r),owner.toString(),rule.toString(),size,(long)(page-1)*size);return new TriggerPage(rows,page,size,total);});}
 public Message read(UUID owner,UUID id){return readBatch(owner,List.of(id)).items().get(0);}
 public ReadResult readBatch(UUID owner,List<UUID> ids){if(ids==null||ids.isEmpty()||ids.size()>50||ids.stream().anyMatch(Objects::isNull)||new HashSet<>(ids).size()!=ids.size())throw invalid();return tx.execute(s->{var sorted=ids.stream().map(UUID::toString).sorted().toList();for(String id:sorted){var locked=db.queryForList("select message_id from inbox_message where owner_id=? and message_id=? for update",owner.toString(),id);if(locked.size()!=1)throw missing();}var now=Timestamp.from(clock.instant());for(String id:sorted)db.update("update inbox_message set read_at=coalesce(read_at,?) where owner_id=? and message_id=?",now,owner.toString(),id);var result=new ArrayList<Message>();for(UUID id:ids)result.add(db.queryForObject(SELECT+" where m.owner_id=? and m.message_id=?",(r,n)->row(r),owner.toString(),id.toString()));return new ReadResult(List.copyOf(result));});}
 private Message row(ResultSet r)throws SQLException{try{var s=mapper.readValue(r.getString("snapshot_json"),Snapshot.class);var e=s.evidence();var read=r.getTimestamp("read_at");return new Message(UUID.fromString(r.getString("message_id")),UUID.fromString(r.getString("trigger_id")),s.ruleId(),s.instrumentId(),s.tradeDate(),s.condition(),s.threshold(),e.rawClose(),e.previousBackwardClose(),e.currentBackwardClose(),e.definition(),e.id(),e.rawVersionId(),e.capturedAt(),r.getTimestamp("created_at").toInstant(),read==null?null:read.toInstant());}catch(SQLException error){throw error;}catch(Exception error){throw new BusinessException("INBOX_RECORD_INVALID","消息记录暂不可读",HttpStatus.INTERNAL_SERVER_ERROR);}}
 static void validatePage(int page,int size){if(page<1||page>100000||size<1||size>50)throw invalid();}
 static BusinessException invalid(){return new BusinessException("VALIDATION_FAILED","消息参数无效，请检查分页和消息编号",HttpStatus.BAD_REQUEST);}
 static BusinessException missing(){return new BusinessException("RESOURCE_NOT_FOUND","记录不存在",HttpStatus.NOT_FOUND);}
}
