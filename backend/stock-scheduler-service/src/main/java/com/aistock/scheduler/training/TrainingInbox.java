package com.aistock.scheduler.training;

import com.aistock.common.BusinessException;
import com.aistock.common.training.TrainingProgress;
import com.aistock.common.training.TrainingSubmission;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class TrainingInbox {
    public record Envelope(String taskId,String configurationId,String configurationHash,String datasetHash) {
        public Envelope {
            validateId(taskId);
            if(!TrainingSubmission.isConfigurationId(configurationId)||!hash(configurationHash)||!hash(datasetHash))
                throw invalid();
        }
        private static boolean hash(String value) {
            return value!=null&&value.matches("[a-f0-9]{64}");
        }
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final TrainingExecutionQueue execution;

    public TrainingInbox(JdbcTemplate jdbc,Clock clock) {
        this.jdbc=jdbc;
        this.clock=clock;
        this.execution=new TrainingExecutionQueue(jdbc,clock);
        this.transaction=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));
        this.transaction.setTimeout(5);
    }

    public Envelope receive(Envelope input) {
        return transaction.execute(status->{
            jdbc.update("""
                INSERT INTO training_dispatch_inbox(task_id,configuration_id,configuration_hash,dataset_hash,received_at)
                VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE task_id=task_id
                """,input.taskId(),input.configurationId(),input.configurationHash(),input.datasetHash(),
                Timestamp.from(clock.instant()));
            var original=read(input.taskId(),true);
            if(!input.equals(original))throw new BusinessException(
                "TRAINING_DISPATCH_CONFLICT","训练任务编号已绑定其他配置",HttpStatus.CONFLICT);
            return original;
        });
    }

    public Envelope get(String taskId) {
        validateId(taskId);
        return read(taskId,false);
    }

    public TrainingProgress cancel(String taskId) {
        validateId(taskId);
        execution.cancelQueued(taskId);
        var progress=status(taskId);
        if(!"CANCELLED".equals(progress.state()))throw new BusinessException(
            "TRAINING_TASK_NOT_CANCELLABLE","只有排队中的训练任务可以取消",HttpStatus.CONFLICT);
        return progress;
    }

    public TrainingProgress status(String taskId) {
        validateId(taskId);
        var rows=jdbc.query("""
            SELECT execution_state,started_at,finished_at,checkpoint_hash,registered_model_id
            FROM training_dispatch_inbox WHERE task_id=?
            """,(row,index)->{
                var start=row.getTimestamp(2);
                var end=row.getTimestamp(3);
                String model=row.getString(5);
                return new TrainingProgress(taskId,row.getString(1),
                    start==null?null:start.toInstant(),end==null?null:end.toInstant(),row.getString(4),
                    model==null?null:java.util.UUID.fromString(model));
            },taskId);
        if(rows.isEmpty())throw new BusinessException("TRAINING_DISPATCH_NOT_FOUND","未找到训练交接记录",HttpStatus.NOT_FOUND);
        return rows.get(0);
    }

    private Envelope read(String taskId,boolean lock) {
        var rows=jdbc.query("""
            SELECT task_id,configuration_id,configuration_hash,dataset_hash
            FROM training_dispatch_inbox WHERE task_id=?
            """+(lock?" FOR UPDATE":""),(row,index)->new Envelope(row.getString(1),row.getString(2),row.getString(3),row.getString(4)),taskId);
        if(rows.size()!=1)throw new BusinessException("TRAINING_DISPATCH_NOT_FOUND","未找到训练交接记录",HttpStatus.NOT_FOUND);
        return rows.get(0);
    }

    private static void validateId(String taskId) {
        if(taskId==null||!taskId.matches("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}"))throw invalid();
    }

    static BusinessException invalid() {
        return new BusinessException("TRAINING_DISPATCH_INVALID","训练交接请求格式不正确",HttpStatus.BAD_REQUEST);
    }
}
