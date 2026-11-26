package com.aistock.market.refresh;
import com.aistock.common.BusinessException;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
@Repository public class RefreshRepository {
    private final JdbcTemplate jdbc;private final TransactionTemplate tx;
    public RefreshRepository(JdbcTemplate jdbc){this.jdbc=jdbc;this.tx=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));}
    public RefreshResult begin(RefreshRequest r,Clock clock) {
        return tx.execute(status->{
            int inserted=jdbc.update("INSERT IGNORE INTO market_refresh_receipt(operation_id,request_hash,instrument_id,target_date,lease_until,state,created_at) VALUES (?,?,?,?,?,'RUNNING',?)",r.operationId(),hash(r),r.instrumentId(),r.targetDate(),Timestamp.from(r.leaseUntil().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)),Timestamp.from(clock.instant()));
            var row=jdbc.queryForMap("SELECT * FROM market_refresh_receipt WHERE operation_id=? FOR UPDATE",r.operationId());
            if(!hash(r).equals(row.get("request_hash")))throw error("REFRESH_OPERATION_CONFLICT");
            if(!"RUNNING".equals(row.get("state")))return result(row);
            if(inserted==1)return null;
            var published=jdbc.queryForList("SELECT version_id FROM market_ingestion_operation WHERE operation_id=? AND state='AVAILABLE' AND instrument_id=? AND provider='baostock'",String.class,r.operationId(),r.instrumentId());
            if(!published.isEmpty()) {var recovered=new RefreshResult(r.operationId(),"AVAILABLE",published.get(0),null);finish(recovered,clock.instant());return recovered;}
            if(!clock.instant().isBefore(r.leaseUntil())) {var expired=new RefreshResult(r.operationId(),"FAILED",null,"ATTEMPT_EXPIRED");finish(expired,clock.instant());return expired;}
            throw error("REFRESH_RUNNING");
        });
    }
    public Head currentHead(String id) {
        var rows=jdbc.query("SELECT h.version_id,h.revision,v.coverage_start FROM instrument_data_head h JOIN market_data_version v ON v.version_id=h.version_id AND v.instrument_id=h.instrument_id AND v.provider=h.provider AND v.price_basis=h.price_basis JOIN instrument i ON i.instrument_id=h.instrument_id WHERE h.instrument_id=? AND h.provider='baostock' AND h.price_basis='RAW' AND v.status='AVAILABLE' AND i.demo_only=false",(rs,n)->new Head(rs.getString(1),rs.getLong(2),rs.getDate(3).toLocalDate()),id);
        return rows.stream().findFirst().orElse(null);
    }
    public Head head(String id) {
        var current=currentHead(id);
        if(current==null)throw missingHead();
        return current;
    }
    public boolean canStart(String id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM instrument WHERE instrument_id=? AND demo_only=false AND listing_status='ACTIVE' AND name_verified=true)",
            Boolean.class,id));
    }
    static BusinessException missingHead() {
        return new BusinessException("REAL_MARKET_VERSION_REQUIRED","该股票尚无可更新的真实行情版本",HttpStatus.UNPROCESSABLE_ENTITY);
    }
    public RefreshResult finish(RefreshResult r,Instant now) {
        return tx.execute(status->{
            var row=jdbc.queryForMap("SELECT * FROM market_refresh_receipt WHERE operation_id=? FOR UPDATE",r.operationId());
            if(!"RUNNING".equals(row.get("state")))return result(row);
            jdbc.update("UPDATE market_refresh_receipt SET state=?,version_id=?,error_code=?,finished_at=? WHERE operation_id=? AND state='RUNNING'",r.state(),r.versionId(),r.errorCode(),Timestamp.from(now),r.operationId());
            return r;
        });
    }
    public RefreshResult lookup(String operationId,Clock clock) {
        if(operationId==null || !operationId.matches("[A-Za-z0-9_-]{1,128}"))throw new BusinessException("REFRESH_REQUEST_INVALID","操作标识不正确",HttpStatus.BAD_REQUEST);
        return tx.execute(status->{
            var rows=jdbc.queryForList("SELECT * FROM market_refresh_receipt WHERE operation_id=? FOR UPDATE",operationId);
            if(rows.isEmpty())throw new BusinessException("REFRESH_NOT_FOUND","尚未登记该执行操作",HttpStatus.NOT_FOUND);
            var row=rows.get(0);
            if(!"RUNNING".equals(row.get("state")))return result(row);
            var published=jdbc.queryForList("SELECT version_id FROM market_ingestion_operation WHERE operation_id=? AND instrument_id=? AND provider='baostock' AND state='AVAILABLE'",String.class,operationId,row.get("instrument_id"));
            if(!published.isEmpty())return finish(new RefreshResult(operationId,"AVAILABLE",published.get(0),null),clock.instant());
            var deadline=((java.time.LocalDateTime)row.get("lease_until")).toInstant(ZoneOffset.UTC);
            if(!clock.instant().isBefore(deadline))return finish(new RefreshResult(operationId,"FAILED",null,"ATTEMPT_EXPIRED"),clock.instant());
            return result(row);
        });
    }
    public void failure(String id,long revision,String error) {jdbc.update("UPDATE instrument_data_head SET last_error_code=? WHERE instrument_id=? AND provider='baostock' AND price_basis='RAW' AND revision=?",error,id,revision);}
    private RefreshResult result(Map<String,Object> r){return new RefreshResult((String)r.get("operation_id"),(String)r.get("state"),(String)r.get("version_id"),(String)r.get("error_code"));}
    private String hash(RefreshRequest r) {return RefreshRequestHasher.hash(r);}
    private BusinessException error(String code){return new BusinessException(code,"刷新操作标识已使用，请查询原任务",HttpStatus.CONFLICT);}
    public record Head(String versionId,long revision,LocalDate start) {}
}
