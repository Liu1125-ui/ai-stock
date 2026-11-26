package com.aistock.user.registration;

import com.aistock.common.BusinessException;
import com.aistock.user.support.AuthDatabaseFixture;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("mysql")
class RateLimitServiceTest {
    private final byte[] key = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    @Test void malformedRegistrationCountsSourceEvenWhenCallerRollsBack() {
        var db = new AuthDatabaseFixture();
        var limits = new RateLimitService(db.jdbc(), key, new RateLimitService.Policy(5, 2, 10, 100, 60));
        String source = "test-" + UUID.randomUUID();
        var transaction = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(db.jdbc().getDataSource()));
        for (int attempt = 0; attempt < 2; attempt++) {
            var error = assertThrows(BusinessException.class, () -> transaction.executeWithoutResult(status ->
                    limits.reserveRegistration("bad-address", source, db.clock().instant())));
            assertEquals("VALIDATION_FAILED", error.code());
        }
        var limited = assertThrows(BusinessException.class, () -> limits.reserveRegistration(
                UUID.randomUUID() + "@example.com", source, db.clock().instant()));
        assertEquals("RATE_LIMITED", limited.code());
    }
    @Test void malformedLoginFailuresCountSourceWithoutEmailBucket() {
        var db = new AuthDatabaseFixture();
        var limits = new RateLimitService(db.jdbc(), key, new RateLimitService.Policy(5, 30, 10, 1, 60));
        String source = "test-" + UUID.randomUUID();
        var invalid = assertThrows(BusinessException.class, () -> limits.recordLoginFailure("bad-address", source, db.clock().instant()));
        assertEquals("VALIDATION_FAILED", invalid.code());
        var limited = assertThrows(BusinessException.class, () -> limits.recordLoginFailure(
                UUID.randomUUID() + "@example.com", source, db.clock().instant()));
        assertEquals("RATE_LIMITED", limited.code());
        var digest = new com.aistock.user.account.SecretDigests(key);
        assertEquals(1, db.jdbc().queryForObject("select request_count from auth_rate_bucket where bucket_key=?",
                Integer.class, digest.scope("LOGIN_SOURCE", source)));
    }
    @Test void rejectedSourceQuotaDoesNotConsumeAnotherEmailsQuota() {
        var db = new AuthDatabaseFixture();
        var limits = new RateLimitService(db.jdbc(), key, new RateLimitService.Policy(1, 1, 10, 100, 60));
        String source = "test-" + UUID.randomUUID();
        String secondEmail = UUID.randomUUID() + "@example.com";
        limits.reserveRegistration(UUID.randomUUID() + "@example.com", source, db.clock().instant());
        assertThrows(BusinessException.class, () -> limits.reserveRegistration(secondEmail, source, db.clock().instant()));
        assertDoesNotThrow(() -> limits.reserveRegistration(secondEmail, "test-" + UUID.randomUUID(), db.clock().instant()));
    }
    @Test void quotasResetExactlyAtWindowEnd() {
        var db = new AuthDatabaseFixture();
        var limits = new RateLimitService(db.jdbc(), key, new RateLimitService.Policy(1, 1, 10, 100, 60));
        String email = UUID.randomUUID() + "@example.com";
        String source = "test-" + UUID.randomUUID();
        limits.reserveRegistration(email, source, db.clock().instant());
        db.clock().advance(Duration.ofSeconds(3599));
        assertThrows(BusinessException.class, () -> limits.reserveRegistration(email, source, db.clock().instant()));
        db.clock().advance(Duration.ofSeconds(1));
        assertDoesNotThrow(() -> limits.reserveRegistration(email, source, db.clock().instant()));
    }
    @Test void loginFailureLimitsApplyToPairAndSourceAndExpire() {
        var db = new AuthDatabaseFixture();
        var limits = new RateLimitService(db.jdbc(), key, new RateLimitService.Policy(5, 30, 2, 3, 60));
        String email = UUID.randomUUID() + "@example.com";
        String source = "test-" + UUID.randomUUID();
        limits.recordLoginFailure(email, source, db.clock().instant());
        limits.recordLoginFailure(email, source, db.clock().instant());
        assertThrows(BusinessException.class, () -> limits.recordLoginFailure(email, source, db.clock().instant()));
        assertDoesNotThrow(() -> limits.recordLoginFailure(UUID.randomUUID() + "@example.com", source, db.clock().instant()));
        assertThrows(BusinessException.class, () -> limits.recordLoginFailure(UUID.randomUUID() + "@example.com", source, db.clock().instant()));
        db.clock().advance(Duration.ofSeconds(900));
        assertDoesNotThrow(() -> limits.recordLoginFailure(email, source, db.clock().instant()));
    }
    @Test void emailCooldownSurvivesNewServiceInstance() {
        var db = new AuthDatabaseFixture();
        String email = UUID.randomUUID() + "@example.com";
        String source = "test-" + UUID.randomUUID();
        new RateLimitService(db.jdbc(), key).reserveRegistration(email, source, db.clock().instant());
        var afterRestart = new RateLimitService(db.jdbc(), key);
        var error = assertThrows(BusinessException.class, () -> afterRestart.reserveRegistration(email, source, db.clock().instant()));
        assertEquals("RATE_LIMITED", error.code());
        db.clock().advance(Duration.ofSeconds(60));
        assertDoesNotThrow(() -> afterRestart.reserveRegistration(email, source, db.clock().instant()));
    }
    @Test void concurrentRequestsOnlyReserveOneEmailSlot() throws Exception {
        var db = new AuthDatabaseFixture();
        var limits = new RateLimitService(db.jdbc(), key);
        var start = new CountDownLatch(1);
        String email = UUID.randomUUID() + "@example.com";
        String source = "test-" + UUID.randomUUID();
        var executor = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> reserve = () -> {
                start.await();
                try { limits.reserveRegistration(email, source, db.clock().instant()); return true; }
                catch (BusinessException expected) {
                    assertEquals("RATE_LIMITED", expected.code());
                    return false;
                }
            };
            var first = executor.submit(reserve);
            var second = executor.submit(reserve);
            start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }
    @Test void hourlyEmailQuotaCannotBeBypassedByWaitingOnlyForCooldown() {
        var db = new AuthDatabaseFixture();
        var limits = new RateLimitService(db.jdbc(), key);
        String email = UUID.randomUUID() + "@example.com";
        String source = "test-" + UUID.randomUUID();
        for (int attempt = 0; attempt < 5; attempt++) {
            limits.reserveRegistration(email, source, db.clock().instant());
            db.clock().advance(Duration.ofSeconds(60));
        }
        assertThrows(BusinessException.class, () -> limits.reserveRegistration(email, source, db.clock().instant()));
    }
}
