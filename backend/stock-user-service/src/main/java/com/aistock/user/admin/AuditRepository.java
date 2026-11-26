package com.aistock.user.admin;
import com.aistock.common.TraceIds;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
public final class AuditRepository {
    private final JdbcTemplate jdbc;
    public AuditRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public void success(UUID operator,UUID target,String action,String trace,Instant now){
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Audit requires transaction");
        jdbc.update("insert into user_audit(audit_id,operator_id,target_id,action,result,trace_id,occurred_at) values(?,?,?,?,'SUCCESS',?,?)",
                UUID.randomUUID().toString(),operator==null?null:operator.toString(),target.toString(),action,TraceIds.normalize(trace),Timestamp.from(now));
    }
}
