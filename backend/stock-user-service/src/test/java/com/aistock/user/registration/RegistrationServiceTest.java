package com.aistock.user.registration;
import com.aistock.common.BusinessException;
import com.aistock.user.support.AuthDatabaseFixture;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("mysql")
class RegistrationServiceTest {
    private final byte[] key = "0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    @Test void expiryIsCheckedAfterWaitingForTheRegistrationLock() {
        var db=new AuthDatabaseFixture();
        var requesting=new RegistrationService(db.jdbc(),key,new byte[32],db.clock());
        String email=UUID.randomUUID()+"@example.com";
        var challenge=requesting.request(email,"test-"+UUID.randomUUID());
        var mail=RegistrationTestMail.read(db.jdbc(),challenge.challengeId(),new RegistrationPayloadCodec(new byte[32]));
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        java.time.Clock deadlineClock=new java.time.Clock() {
            public java.time.ZoneId getZone(){return java.time.ZoneOffset.UTC;}
            public java.time.Clock withZone(java.time.ZoneId zone){return this;}
            public java.time.Instant instant(){return calls.getAndIncrement()==0?challenge.expiresAt().minusSeconds(1):challenge.expiresAt();}
        };
        var service=new RegistrationService(db.jdbc(),key,new byte[32],deadlineClock);
        assertThrows(BusinessException.class,()->service.register(new RegisterCommand(UUID.randomUUID(),challenge.challengeId(),email,mail.code(),"Long-test-password-1","测试用户")));
        assertEquals(0,db.jdbc().queryForObject("select count(*) from user_account where email_normalized=?",Integer.class,email));
    }
    @Test void successfulVerificationCreatesOrdinaryUserAndRetryDoesNotChangePassword() {
        var db = new AuthDatabaseFixture();
        var service = new RegistrationService(db.jdbc(), key, new byte[32], db.clock());
        String email = UUID.randomUUID() + "@example.com";
        var challenge = service.request(email, "test-" + UUID.randomUUID());
        var mail = RegistrationTestMail.read(db.jdbc(),challenge.challengeId(),new RegistrationPayloadCodec(new byte[32]));
        var command = new RegisterCommand(UUID.randomUUID(), challenge.challengeId(), email, mail.code(), "Long-test-password-1", "测试用户");
        var first = service.register(command);
        var replay = service.register(new RegisterCommand(command.requestId(), command.challengeId(), email, mail.code(), "Another-password-1", "测试用户"));
        assertEquals(first.userId(), replay.userId()); assertTrue(replay.replayed());
        assertEquals("USER", db.jdbc().queryForObject("select role_code from user_account where user_id=?", String.class, first.userId().toString()));
        String hash = db.jdbc().queryForObject("select password_hash from user_account where user_id=?", String.class, first.userId().toString());
        assertTrue(new com.aistock.user.account.PasswordHasher().matches("Long-test-password-1", hash));
        assertFalse(new com.aistock.user.account.PasswordHasher().matches("Another-password-1", hash));
        assertEquals("REQUEST_CONFLICT", assertThrows(BusinessException.class, () -> service.register(
                new RegisterCommand(command.requestId(), command.challengeId(), email, mail.code(), "Long-test-password-1", "不同昵称"))).code());
    }
    @Test void wrongCodesCommitAttemptsAndCorrectSixthAttemptStillFails() {
        var db = new AuthDatabaseFixture();
        var service = new RegistrationService(db.jdbc(), key, new byte[32], db.clock());
        String email = UUID.randomUUID() + "@example.com";
        var challenge = service.request(email, "test-" + UUID.randomUUID());
        var mail = RegistrationTestMail.read(db.jdbc(),challenge.challengeId(),new RegistrationPayloadCodec(new byte[32]));
        String wrong = mail.code().equals("000000") ? "999999" : "000000";
        for (int attempt=0; attempt<5; attempt++) assertEquals("CHALLENGE_INVALID", assertThrows(BusinessException.class, () -> service.register(
                new RegisterCommand(UUID.randomUUID(), challenge.challengeId(), email, wrong, "Long-test-password-1", "测试用户"))).code());
        assertEquals(5, new ChallengeRepository(db.jdbc()).findById(challenge.challengeId()).orElseThrow().failedAttempts());
        assertThrows(BusinessException.class, () -> service.register(new RegisterCommand(UUID.randomUUID(), challenge.challengeId(), email, mail.code(), "Long-test-password-1", "测试用户")));
        assertEquals(0, db.jdbc().queryForObject("select count(*) from user_account where email_normalized=?", Integer.class, email));
    }
    @Test void resendingInvalidatesPreviousChallenge() {
        var db = new AuthDatabaseFixture();
        var service = new RegistrationService(db.jdbc(), key, new byte[32], db.clock());
        String email = UUID.randomUUID() + "@example.com"; String source = "test-" + UUID.randomUUID();
        var old = service.request(email, source);
        var mail = RegistrationTestMail.read(db.jdbc(),old.challengeId(),new RegistrationPayloadCodec(new byte[32]));
        db.clock().advance(java.time.Duration.ofSeconds(60)); service.request(email, source);
        assertThrows(BusinessException.class, () -> service.register(new RegisterCommand(UUID.randomUUID(), old.challengeId(), email, mail.code(), "Long-test-password-1", "测试用户")));
    }
}
