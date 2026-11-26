package com.aistock.user.registration;
import com.aistock.user.support.AuthDatabaseFixture;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("mysql")
class ChallengeRepositoryTest {
    @Test void utcStorageAndReadingPreserveTheChallengeDeadline() {
        var db = new AuthDatabaseFixture();
        var challenges = new ChallengeRepository(db.jdbc());
        UUID id = UUID.randomUUID();
        var expiry = db.clock().instant().plusSeconds(600);
        challenges.create(id, UUID.randomUUID() + "@example.com", "a".repeat(64), "test-key", db.clock().instant(), expiry);
        assertEquals("2026-09-30 00:00:00.000", db.jdbc().queryForObject(
                "select date_format(created_at,'%Y-%m-%d %H:%i:%s.%f') from registration_challenge where challenge_id=?",
                String.class, id.toString()).substring(0, 23));
        assertEquals(expiry, challenges.findById(id).orElseThrow().expiresAt());
    }
    @Test void challengeLockRequiresTransactionAndFindsTheChallenge() {
        var db = new AuthDatabaseFixture();
        var challenges = new ChallengeRepository(db.jdbc());
        UUID id = UUID.randomUUID();
        challenges.create(id, UUID.randomUUID() + "@example.com", "a".repeat(64), "test-key", db.clock().instant(), db.clock().instant().plusSeconds(600));
        assertThrows(IllegalStateException.class, () -> challenges.lockById(id));
        var transaction = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(db.jdbc().getDataSource()));
        transaction.executeWithoutResult(status -> assertEquals(id, challenges.lockById(id).orElseThrow().id()));
    }
    @Test void challengeBeforeAccountExistsHasPersistentFailureLimit() {
        var db = new AuthDatabaseFixture();
        var challenges = new ChallengeRepository(db.jdbc());
        UUID id = UUID.randomUUID();
        challenges.create(id, UUID.randomUUID() + "@example.com", "a".repeat(64), "test-key", db.clock().instant(), db.clock().instant().plusSeconds(600));
        for (int attempt = 0; attempt < 5; attempt++) assertTrue(challenges.recordFailedAttempt(id, db.clock().instant(), 5));
        assertFalse(challenges.recordFailedAttempt(id, db.clock().instant(), 5));
        assertFalse(challenges.consume(id, db.clock().instant(), 5));
        assertEquals(5, challenges.findById(id).orElseThrow().failedAttempts());
    }
    @Test void exactlyAtExpiryCodeIsRejected() {
        var db = new AuthDatabaseFixture();
        var challenges = new ChallengeRepository(db.jdbc());
        UUID id = UUID.randomUUID();
        var expiry = db.clock().instant().plusSeconds(600);
        challenges.create(id, UUID.randomUUID() + "@example.com", "a".repeat(64), "test-key", db.clock().instant(), expiry);
        assertFalse(challenges.consume(id, expiry, 5));
    }
    @Test void twoConcurrentConsumersOnlyConsumeOnce() throws Exception {
        var db = new AuthDatabaseFixture();
        var challenges = new ChallengeRepository(db.jdbc());
        UUID id = UUID.randomUUID();
        challenges.create(id, UUID.randomUUID() + "@example.com", "a".repeat(64), "test-key", db.clock().instant(), db.clock().instant().plusSeconds(600));
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> consume = () -> { start.await(); return challenges.consume(id, db.clock().instant(), 5); };
            var a = executor.submit(consume); var b = executor.submit(consume);
            start.countDown();
            assertNotEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }
}
