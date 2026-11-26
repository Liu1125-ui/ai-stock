package com.aistock.user.registration;
import com.aistock.common.BusinessException;
import com.aistock.user.support.AuthDatabaseFixture;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("mysql")
class RegistrationConcurrencyTest {
    @Test void parallelRegistrationsConsumeOneChallengeOnce() throws Exception {
        var db = new AuthDatabaseFixture();
        byte[] key = new byte[32];
        var service = new RegistrationService(db.jdbc(), key, key, db.clock());
        String email = UUID.randomUUID() + "@example.com";
        var challenge = service.request(email, "test-" + UUID.randomUUID());
        var mail = RegistrationTestMail.read(db.jdbc(),challenge.challengeId(),new RegistrationPayloadCodec(key));
        var start = new java.util.concurrent.CountDownLatch(1); var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> register = () -> { start.await(); try {
                service.register(new RegisterCommand(UUID.randomUUID(), challenge.challengeId(), email, mail.code(), "Long-test-password-1", "测试用户")); return true;
            } catch(BusinessException rejected) { assertEquals("CHALLENGE_INVALID", rejected.code()); return false; } };
            var a=executor.submit(register); var b=executor.submit(register); start.countDown();
            assertNotEquals(a.get(15,java.util.concurrent.TimeUnit.SECONDS), b.get(15,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1,db.jdbc().queryForObject("select count(*) from user_account where email_normalized=?", Integer.class,email));
        } finally { executor.shutdownNow(); }
    }
}
