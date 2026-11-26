package com.aistock.prediction.modelcatalog;
import com.aistock.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.aistock.prediction.modelcatalog.ModelRecords.*;
public final class ModelRepository {
    private final JdbcTemplate jdbc;private final ObjectMapper mapper;private final TransactionTemplate tx;
    public ModelRepository(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;tx=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));}
    public <T>T locked(Function<Head,T> work){return tx.execute(status->work.apply(jdbc.queryForObject("select * from model_release_head where scope_key=? for update",(r,n)->head(r),SCOPE)));}
    private Head head(ResultSet r)throws SQLException{var id=r.getString("current_model_id");return new Head(r.getString("scope_key"),id==null?null:UUID.fromString(id),r.getLong("revision"));}
    public Head head(){return jdbc.queryForObject("select * from model_release_head where scope_key=?",(r,n)->head(r),SCOPE);}
    private Version version(ResultSet r)throws SQLException{
        try{return new Version(UUID.fromString(r.getString("model_id")),r.getString("artifact_id"),r.getString("manifest_hash"),r.getString("checkpoint_hash"),r.getString("status"),r.getLong("revision"),r.getBoolean("ever_published"),r.getString("registration_hash"),mapper.readTree(r.getString("manifest_json")),mapper.readValue(r.getString("evaluation_json"),Evaluation.class),r.getTimestamp("created_at").toInstant());}catch(java.io.IOException e){throw new BusinessException("MODEL_METADATA_INVALID","模型记录暂不可用",HttpStatus.INTERNAL_SERVER_ERROR);}
    }
    public Version find(UUID id,boolean lock){var rows=jdbc.query("select * from model_version where model_id=?"+(lock?" for update":""),(r,n)->version(r),id.toString());if(rows.isEmpty())throw new BusinessException("RESOURCE_NOT_FOUND","模型版本不存在",HttpStatus.NOT_FOUND);return rows.get(0);}
    public Version byArtifact(String artifact){return jdbc.query("select * from model_version where artifact_id=?",(r,n)->version(r),artifact).stream().findFirst().orElse(null);}
    public List<Version> list(){return jdbc.query("select * from model_version order by created_at,model_id limit 100",(r,n)->version(r));}
    public List<Version> published(){return jdbc.query("select * from model_version where status='PUBLISHED' order by created_at,model_id limit 100",(r,n)->version(r));}
    public void insert(Version v){jdbc.update("insert into model_version(model_id,artifact_id,manifest_hash,checkpoint_hash,status,revision,ever_published,registration_hash,manifest_json,evaluation_json,created_at) values(?,?,?,?,?,?,?,?,?,?,?)",v.modelId().toString(),v.artifactId(),v.manifestHash(),v.checkpointHash(),v.state(),v.revision(),v.everPublished(),v.registrationHash(),json(mapper,v.manifest()),json(mapper,v.evaluation()),Timestamp.from(v.createdAt()));}
    public void state(UUID id,String state,boolean ever,long revision){jdbc.update("update model_version set status=?,ever_published=?,revision=? where model_id=?",state,ever,revision,id.toString());}
    public void head(Head h){jdbc.update("update model_release_head set current_model_id=?,revision=? where scope_key=?",h.currentModelId()==null?null:h.currentModelId().toString(),h.revision(),SCOPE);}
    public Operation operation(UUID id){return jdbc.query("select actor_id,request_hash,result_json from model_release_event where operation_id=?",(r,n)->{try{return new Operation(UUID.fromString(r.getString("actor_id")),r.getString("request_hash"),mapper.readValue(r.getString("result_json"),ActionResult.class));}catch(java.io.IOException e){throw new BusinessException("MODEL_METADATA_INVALID","操作记录暂不可用",HttpStatus.INTERNAL_SERVER_ERROR);}},id.toString()).stream().findFirst().orElse(null);}
    public void event(Event e,String hash,ActionResult result){jdbc.update("insert into model_release_event(event_id,operation_id,actor_id,model_id,action_code,reason,request_hash,result_json,created_at) values(?,?,?,?,?,?,?,?,?)",e.eventId().toString(),e.operationId()==null?null:e.operationId().toString(),e.actorId()==null?null:e.actorId().toString(),e.modelId().toString(),e.action(),e.reason(),hash,json(mapper,result),Timestamp.from(e.createdAt()));}
    public List<Event> events(UUID id){find(id,false);return jdbc.query("select * from model_release_event where model_id=? order by event_sequence",(r,n)->new Event(UUID.fromString(r.getString("event_id")),r.getString("operation_id")==null?null:UUID.fromString(r.getString("operation_id")),r.getString("actor_id")==null?null:UUID.fromString(r.getString("actor_id")),id,r.getString("action_code"),r.getString("reason"),r.getTimestamp("created_at").toInstant()),id.toString());}
}
