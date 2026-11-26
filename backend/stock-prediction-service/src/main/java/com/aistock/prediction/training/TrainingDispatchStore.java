package com.aistock.prediction.training;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

public final class TrainingDispatchStore {
    public record Claim(TrainingAdmissionRepository.Item item,String token,boolean reconcileFirst) {}
    public enum Outcome { CONFIRMED, CONFLICT, RETRY }

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public TrainingDispatchStore(JdbcTemplate jdbc,Clock clock) {
        this.jdbc=jdbc;
        this.clock=clock;
        transaction=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(5);
    }

    public Claim claim() {
        return transaction.execute(status->{
            var now=clock.instant();
            var rows=jdbc.query("""
                SELECT task_id,configuration_id,configuration_sha256,dataset_sha256,dispatch_started
                FROM training_submission_item
                WHERE dispatch_state='PENDING' AND dispatch_due_at<=?
                ORDER BY dispatch_due_at,task_id LIMIT 1 FOR UPDATE SKIP LOCKED
                """,(row,index)->new Claim(new TrainingAdmissionRepository.Item(
                    row.getString(1),row.getString(2),row.getString(3),row.getString(4)),
                    UUID.randomUUID().toString(),row.getBoolean(5)),Timestamp.from(now));
            if(rows.isEmpty())return null;
            var claim=rows.get(0);
            jdbc.update("""
                UPDATE training_submission_item SET dispatch_started=TRUE,dispatch_claim=?,dispatch_due_at=?
                WHERE task_id=?
                """,claim.token(),Timestamp.from(now.plusSeconds(30)),claim.item().taskId());
            return claim;
        });
    }

    public boolean finish(Claim claim,Outcome outcome) {
        return transaction.execute(status->{
            var matching=jdbc.queryForList("""
                SELECT task_id FROM training_submission_item
                WHERE task_id=? AND dispatch_claim=? AND dispatch_state='PENDING' FOR UPDATE
                """,String.class,claim.item().taskId(),claim.token());
            if(matching.isEmpty())return false;
            var now=clock.instant();
            return jdbc.update("""
                UPDATE training_submission_item SET dispatch_state=?,dispatch_claim=NULL,dispatch_due_at=?
                WHERE task_id=? AND dispatch_claim=? AND dispatch_state='PENDING' AND dispatch_due_at>?
                """,outcome==Outcome.RETRY?"PENDING":outcome.name(),
                Timestamp.from(outcome==Outcome.RETRY?now.plusSeconds(60):now),
                claim.item().taskId(),claim.token(),Timestamp.from(now))==1;
        });
    }
}
