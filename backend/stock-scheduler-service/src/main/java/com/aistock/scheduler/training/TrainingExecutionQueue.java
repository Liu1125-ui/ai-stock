package com.aistock.scheduler.training;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** One persisted execution slot. Only confirmed terminal completion may release it. */
public final class TrainingExecutionQueue {
    public record Claim(TrainingInbox.Envelope item,String token) {}
    public enum Terminal { SUCCEEDED, FAILED }

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public TrainingExecutionQueue(JdbcTemplate jdbc,Clock clock) {
        this.jdbc=jdbc;
        this.clock=clock;
        transaction=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(5);
    }

    public Claim claim() {
        return transaction.execute(status->{
            if(lockedTask()!=null)return null;
            var queued=jdbc.query("""
                SELECT task_id,configuration_id,configuration_hash,dataset_hash
                FROM training_dispatch_inbox WHERE execution_state='QUEUED'
                ORDER BY received_at,task_id LIMIT 1 FOR UPDATE
                """,(row,index)->new TrainingInbox.Envelope(row.getString(1),row.getString(2),row.getString(3),row.getString(4)));
            if(queued.isEmpty())return null;
            var item=queued.get(0);
            String token=UUID.randomUUID().toString();
            jdbc.update("""
                UPDATE training_dispatch_inbox SET execution_state='RUNNING',execution_token=?,started_at=?
                WHERE task_id=?
                """,token,Timestamp.from(clock.instant()),item.taskId());
            jdbc.update("UPDATE training_execution_slot SET task_id=? WHERE slot_id=1",item.taskId());
            return new Claim(item,token);
        });
    }

    public Claim running() {
        var rows=jdbc.query("""
            SELECT q.task_id,q.configuration_id,q.configuration_hash,q.dataset_hash,q.execution_token
            FROM training_execution_slot s JOIN training_dispatch_inbox q ON s.task_id=q.task_id
            WHERE s.slot_id=1 AND q.execution_state='RUNNING'
            """,(row,index)->new Claim(new TrainingInbox.Envelope(row.getString(1),row.getString(2),
                row.getString(3),row.getString(4)),row.getString(5)));
        return rows.isEmpty()?null:rows.get(0);
    }

    public boolean cancelQueued(String taskId) {
        return transaction.execute(status->jdbc.update("""
            UPDATE training_dispatch_inbox SET execution_state='CANCELLED',finished_at=?
            WHERE task_id=? AND execution_state='QUEUED'
            """,Timestamp.from(clock.instant()),taskId)==1);
    }

    public boolean finish(Claim claim,Terminal terminal,String checkpointHash) {
        if(terminal==Terminal.SUCCEEDED?(checkpointHash==null||!checkpointHash.matches("[a-f0-9]{64}")):checkpointHash!=null)
            throw new IllegalArgumentException("Invalid terminal training checkpoint");
        return transaction.execute(status->{
            if(!claim.item().taskId().equals(lockedTask()))return false;
            int changed=jdbc.update("""
                UPDATE training_dispatch_inbox SET execution_state=?,execution_token=NULL,finished_at=?,checkpoint_hash=?
                WHERE task_id=? AND execution_state='RUNNING' AND execution_token=?
                """,terminal.name(),Timestamp.from(clock.instant()),checkpointHash,claim.item().taskId(),claim.token());
            if(changed!=1)return false;
            jdbc.update("UPDATE training_execution_slot SET task_id=NULL WHERE slot_id=1");
            return true;
        });
    }

    private String lockedTask() {
        return jdbc.queryForObject("SELECT task_id FROM training_execution_slot WHERE slot_id=1 FOR UPDATE",String.class);
    }
}
