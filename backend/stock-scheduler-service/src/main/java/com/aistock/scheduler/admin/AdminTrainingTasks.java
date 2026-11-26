package com.aistock.scheduler.admin;

import com.aistock.common.BusinessException;
import com.aistock.scheduler.training.TrainingExecutionQueue;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import static com.aistock.scheduler.admin.AdminTaskActionRecords.*;

@Service
public final class AdminTrainingTasks {
    private static final int PAGE_SIZE=20;
    private static final String CANCELLED="CANCELLED";
    private static final String CANCEL_MESSAGE="训练任务已取消";
    private final JdbcTemplate jdbc;
    private final TrainingExecutionQueue queue;
    private final TransactionTemplate transaction;
    private final boolean actionsEnabled;

    public record Cancellation(String operationId,String actorUserId,String reasonCode,Instant cancelledAt) {}
    public record Row(String taskId,String configurationId,String state,Instant receivedAt,Instant startedAt,
                      Instant finishedAt,String modelId,Cancellation cancellation) {}
    public record Page(List<Row> items,boolean hasMore,boolean actionsEnabled) {}

    public AdminTrainingTasks(JdbcTemplate jdbc,Clock clock,
                              @Value("${aistock.admin-task.actions-enabled:false}") boolean actionsEnabled) {
        this.jdbc=jdbc;
        this.queue=new TrainingExecutionQueue(jdbc,clock);
        this.transaction=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));
        this.transaction.setTimeout(5);
        this.actionsEnabled=actionsEnabled;
    }

    public Page list(int page) {
        if(page<1||page>100)throw AdminTaskActionInput.invalid();
        var rows=jdbc.query("""
            SELECT q.task_id,q.configuration_id,q.execution_state,q.received_at,q.started_at,q.finished_at,
                   q.registered_model_id,a.operation_id,a.actor_user_id,a.reason_code,a.cancelled_at
            FROM training_dispatch_inbox q LEFT JOIN training_admin_cancellation a ON a.task_id=q.task_id
            ORDER BY q.received_at DESC,q.task_id DESC LIMIT ? OFFSET ?
            """,(r,index)->new Row(r.getString("task_id"),r.getString("configuration_id"),r.getString("execution_state"),
                instant(r,"received_at"),instant(r,"started_at"),instant(r,"finished_at"),
                r.getString("registered_model_id"),r.getString("operation_id")==null?null:audit(r)),
            PAGE_SIZE+1,(page-1)*PAGE_SIZE);
        return new Page(List.copyOf(rows.subList(0,Math.min(rows.size(),PAGE_SIZE))),page<100&&rows.size()>PAGE_SIZE,actionsEnabled);
    }

    public Receipt cancel(UUID actor,UUID task,Command command) {
        if(!"0".equals(command.expectedRevision()))
            throw conflict("TASK_REVISION_CONFLICT","任务状态已变化，请刷新后重新确认");
        try {
            return transaction.execute(status->{
                var taskId=task.toString();
                var states=jdbc.queryForList("SELECT execution_state FROM training_dispatch_inbox WHERE task_id=? FOR UPDATE",String.class,taskId);
                if(states.isEmpty())throw new BusinessException("TASK_NOT_FOUND","训练任务不存在",HttpStatus.NOT_FOUND);
                var audits=jdbc.query("SELECT * FROM training_admin_cancellation WHERE task_id=?",(row,index)->audit(row),taskId);
                if(!audits.isEmpty()) {
                    var audit=audits.get(0);
                    if(audit.operationId().equals(command.operationId().toString())) {
                        if(!audit.actorUserId().equals(actor.toString())||!audit.reasonCode().equals(command.reasonCode()))
                            throw operationConflict();
                        return receipt(taskId,audit);
                    }
                }
                if(!actionsEnabled)throw conflict("TASK_ACTIONS_DISABLED","管理员任务操作入口尚未开启");
                if(!queue.cancelQueued(taskId))throw conflict("TASK_ACTION_NOT_ALLOWED","只有排队中的训练任务可以取消");
                var completed=jdbc.queryForObject("SELECT finished_at FROM training_dispatch_inbox WHERE task_id=?",
                    (row,index)->instant(row,"finished_at"),taskId);
                var audit=new Cancellation(command.operationId().toString(),actor.toString(),command.reasonCode(),completed);
                jdbc.update("""
                    INSERT INTO training_admin_cancellation(task_id,operation_id,actor_user_id,reason_code,cancelled_at)
                    SELECT task_id,?,?,?,finished_at FROM training_dispatch_inbox WHERE task_id=?
                    """,audit.operationId(),audit.actorUserId(),audit.reasonCode(),taskId);
                return receipt(taskId,audit);
            });
        } catch(DuplicateKeyException duplicate) {
            throw operationConflict();
        }
    }

    private static Receipt receipt(String taskId,Cancellation audit) {
        return new Receipt(audit.operationId(),taskId,"CANCEL","APPLIED","TASK_CANCEL_APPLIED",CANCEL_MESSAGE,
            "QUEUED",CANCELLED,"0","1",audit.cancelledAt(),audit.cancelledAt());
    }
    private static Cancellation audit(ResultSet row) throws SQLException {
        return new Cancellation(row.getString("operation_id"),row.getString("actor_user_id"),row.getString("reason_code"),
            instant(row,"cancelled_at"));
    }
    private static Instant instant(ResultSet row,String name) throws SQLException {
        var value=row.getTimestamp(name);
        return value==null?null:value.toInstant();
    }
    private static BusinessException operationConflict() {
        return conflict("OPERATION_KEY_CONFLICT","操作编号与原请求不一致");
    }
    private static BusinessException conflict(String code,String message) {
        return new BusinessException(code,message,HttpStatus.CONFLICT);
    }
}
