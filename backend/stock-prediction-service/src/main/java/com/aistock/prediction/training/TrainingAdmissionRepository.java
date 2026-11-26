package com.aistock.prediction.training;

import com.aistock.common.BusinessException;
import com.aistock.common.training.TrainingSubmission;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Persists admission and original task identities before dispatch to the scheduler. */
public final class TrainingAdmissionRepository {
    public record Configuration(String configurationHash,String datasetHash) {
        public Configuration {
            if(!hash(configurationHash)||!hash(datasetHash))throw new IllegalArgumentException("Invalid training configuration");
        }
        private static boolean hash(String value) {
            return value!=null&&value.matches("[a-f0-9]{64}");
        }
    }
    public record Item(String taskId,String configurationId,String configurationHash,String datasetHash) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public TrainingAdmissionRepository(JdbcTemplate jdbc,ObjectMapper mapper,Clock clock) {
        this.jdbc=jdbc;
        this.mapper=mapper;
        this.clock=clock;
        this.transaction=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));
    }

    /** Configuration lookup must be local and side-effect-free. */
    public List<Item> admit(UUID owner,TrainingSubmission request,Function<String,Configuration> catalog) {
        String ownerId=owner.toString();
        String requested=mapper.valueToTree(request.configurationIds()).toString();
        return transaction.execute(status->{
            jdbc.update("""
                INSERT INTO training_submission(owner_id,request_id,configuration_ids,created_at)
                VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE request_id=request_id
                """,ownerId,request.requestId(),requested,Timestamp.from(clock.instant()));
            String original=jdbc.queryForObject("""
                SELECT configuration_ids FROM training_submission WHERE owner_id=? AND request_id=? FOR UPDATE
                """,String.class,ownerId,request.requestId());
            if(!mapper.valueToTree(request.configurationIds()).equals(read(original)))throw new BusinessException(
                "TRAINING_REQUEST_CONFLICT","该请求编号已绑定其他训练配置",HttpStatus.CONFLICT);
            var existing=items(ownerId,request.requestId(),true);
            if(!existing.isEmpty())return existing;
            for(String id:request.configurationIds()) {
                var configuration=catalog.apply(id);
                if(configuration==null)throw new BusinessException(
                    "TRAINING_CONFIGURATION_UNAVAILABLE","训练配置暂不可用",HttpStatus.BAD_REQUEST);
                jdbc.update("""
                    INSERT INTO training_submission_item(task_id,owner_id,request_id,configuration_id,configuration_sha256,dataset_sha256)
                    VALUES (?,?,?,?,?,?)
                    """,UUID.randomUUID().toString(),ownerId,request.requestId(),id,
                    configuration.configurationHash(),configuration.datasetHash());
            }
            return items(ownerId,request.requestId(),true);
        });
    }

    public List<Item> receipt(UUID owner,String request) {
        if(request==null||!request.matches("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}"))
            throw new BusinessException("TRAINING_REQUEST_INVALID","训练请求格式不正确",HttpStatus.BAD_REQUEST);
        var result=items(owner.toString(),request,false);
        if(result.isEmpty())throw new BusinessException("TRAINING_REQUEST_NOT_FOUND","未找到训练接单记录",HttpStatus.NOT_FOUND);
        return result;
    }

    public record Summary(String requestId,java.time.Instant createdAt) {}
    public record History(List<Summary> items,String nextCursor) {}
    public record OwnedItem(String taskId,String configurationId,String datasetHash,String dispatchState) {}

    public History history(UUID owner,String before) {
        String ownerId=owner.toString();
        String filter="";
        Object[] params={ownerId};
        if(before!=null) {
            var cursor=jdbc.query("SELECT created_at FROM training_submission WHERE owner_id=? AND request_id=?",
                (row,index)->row.getTimestamp(1),ownerId,before);
            if(cursor.isEmpty())throw notFound();
            filter=" AND (created_at<? OR (created_at=? AND request_id<?))";
            params=new Object[]{ownerId,cursor.get(0),cursor.get(0),before};
        }
        var rows=jdbc.query("SELECT request_id,created_at FROM training_submission WHERE owner_id=?"
            +filter+" ORDER BY created_at DESC,request_id DESC LIMIT 21",
            (row,index)->new Summary(row.getString(1),row.getTimestamp(2).toInstant()),params);
        var page=rows.stream().limit(20).toList();
        return new History(page,rows.size()>20?page.get(19).requestId():null);
    }

    public List<OwnedItem> ownedItems(UUID owner,String request) {
        var rows=jdbc.query("""
            SELECT task_id,configuration_id,dataset_sha256,dispatch_state
            FROM training_submission_item WHERE owner_id=? AND request_id=? ORDER BY configuration_id
            """,(row,index)->new OwnedItem(row.getString(1),row.getString(2),row.getString(3),row.getString(4)),
            owner.toString(),request);
        if(rows.isEmpty())throw notFound();
        return rows;
    }

    static BusinessException notFound() {
        return new BusinessException("TRAINING_REQUEST_NOT_FOUND","未找到训练接单记录",HttpStatus.NOT_FOUND);
    }

    private List<Item> items(String owner,String request,boolean lock) {
        return jdbc.query("""
            SELECT task_id,configuration_id,configuration_sha256,dataset_sha256
            FROM training_submission_item WHERE owner_id=? AND request_id=? ORDER BY configuration_id
            """+(lock?" FOR UPDATE":""),(row,index)->new Item(row.getString(1),row.getString(2),row.getString(3),row.getString(4)),owner,request);
    }

    private com.fasterxml.jackson.databind.JsonNode read(String value) {
        try {return mapper.readTree(value);}
        catch(java.io.IOException error) {throw new IllegalStateException("Stored training request is invalid",error);}
    }
}
