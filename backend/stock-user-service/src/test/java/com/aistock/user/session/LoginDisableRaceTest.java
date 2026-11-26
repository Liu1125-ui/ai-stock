package com.aistock.user.session;
import com.aistock.common.BusinessException;
import com.aistock.user.support.AuthDatabaseFixture;
import com.aistock.user.account.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import static org.junit.jupiter.api.Assertions.*;

@Tag("mysql")
class LoginDisableRaceTest {
    @Test void disableWhilePasswordVerificationIsInFlightCannotIssueValidSession() throws Exception {
        var db=new AuthDatabaseFixture(); String email=UUID.randomUUID()+"@example.com"; UUID id=UUID.randomUUID();
        new UserRepository(db.jdbc()).insertVerifiedOrdinaryUser(id,email,new PasswordHasher().encode("Long-test-password-1"),"测试用户",db.clock().instant());
        var reachesLock=new java.util.concurrent.CountDownLatch(1);
        var observing=new org.springframework.jdbc.core.JdbcTemplate(db.jdbc().getDataSource()) {
            @Override public <T> java.util.List<T> query(String sql,org.springframework.jdbc.core.RowMapper<T> mapper,Object... args) {
                if(sql.toLowerCase(java.util.Locale.ROOT).contains("for update")) reachesLock.countDown();
                return super.query(sql,mapper,args);
            }
        };
        var sessions=new SessionService(observing,new byte[32],db.clock());
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        var holder=new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<String>>();
        try {
            var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(db.jdbc().getDataSource()));
            tx.executeWithoutResult(status->{
                db.jdbc().queryForObject("select user_id from user_account where user_id=? for update",String.class,id.toString());
                holder.set(executor.submit(()->{try {sessions.login(email,"Long-test-password-1","test-source"); return "ISSUED";}catch(BusinessException error){return error.code();}}));
                try { assertTrue(reachesLock.await(15,java.util.concurrent.TimeUnit.SECONDS)); }
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
                db.jdbc().update("update user_account set status='DISABLED',credential_version=credential_version+1 where user_id=?",id.toString());
            });
            assertEquals("LOGIN_FAILED",holder.get().get(15,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(0,db.jdbc().queryForObject("select count(*) from login_session",Integer.class));
        } finally {executor.shutdownNow();}
    }
}
