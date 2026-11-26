package com.aistock.user.admin;
import com.aistock.user.support.AuthDatabaseFixture;
import com.aistock.common.BusinessException;
import java.util.concurrent.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
@Tag("mysql") class AdminBootstrapConcurrencyTest {
    @Test void concurrentInitializationCreatesExactlyOneAdminAndAudit() throws Exception {
        var db=new AuthDatabaseFixture();var command=new AdminBootstrapCommand(db.jdbc(),db.clock());
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            Callable<Boolean> task=()->{start.await();try{command.initialize("admin@example.com","Long-admin-password-1","管理员","bootstrap");return true;}catch(BusinessException e){assertEquals("ADMIN_ALREADY_INITIALIZED",e.code());return false;}};
            var one=pool.submit(task);var two=pool.submit(task);start.countDown();
            assertNotEquals(one.get(15,TimeUnit.SECONDS),two.get(15,TimeUnit.SECONDS));
            assertEquals(1,db.jdbc().queryForObject("select count(*) from user_account where role_code='ADMIN'",Integer.class));
            assertEquals(1,db.jdbc().queryForObject("select count(*) from user_audit where action='ADMIN_BOOTSTRAP'",Integer.class));
        }finally{pool.shutdownNow();}
    }
}
