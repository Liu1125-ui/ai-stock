package com.aistock.user.registration;

import com.aistock.user.account.EmailAddress;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/** Atomic state transitions; registration orchestration supplies the surrounding transaction. */
public final class ChallengeRepository {
    public record Challenge(UUID id, String email, String digest, String keyId, Instant expiresAt,
                            int failedAttempts, Instant consumedAt, Instant invalidatedAt) {
        @Override public String toString() { return "Challenge[id=" + id + ",failedAttempts=" + failedAttempts + "]"; }
    }
    private static final RowMapper<Challenge> MAPPER = (row, index) -> new Challenge(
            UUID.fromString(row.getString("challenge_id")), row.getString("email_normalized"),
            row.getString("code_digest"), row.getString("digest_key_id"), row.getTimestamp("expires_at").toInstant(),
            row.getInt("failed_attempts"), instant(row.getTimestamp("consumed_at")), instant(row.getTimestamp("invalidated_at")));
    private final JdbcTemplate jdbc;

    public ChallengeRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void create(UUID id, String email, String digest, String keyId, Instant createdAt, Instant expiresAt) {
        jdbc.update("""
                insert into registration_challenge(challenge_id,email_normalized,code_digest,digest_key_id,
                    purpose,created_at,expires_at) values (?,?,?,?,'REGISTER',?,?)
                """, id.toString(), EmailAddress.normalize(email), digest, keyId,
                Timestamp.from(createdAt), Timestamp.from(expiresAt));
    }

    public Optional<Challenge> findById(UUID id) {
        return jdbc.query("select * from registration_challenge where purpose='REGISTER' and challenge_id=?", MAPPER, id.toString()).stream().findFirst();
    }

    public Optional<Challenge> lockById(UUID id) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Challenge row lock requires an active transaction");
        }
        return jdbc.query("select * from registration_challenge where purpose='REGISTER' and challenge_id=? for update",
                MAPPER, id.toString()).stream().findFirst();
    }

    public boolean recordFailedAttempt(UUID id, Instant now, int maxFailures) {
        return jdbc.update("""
                update registration_challenge set failed_attempts=failed_attempts+1
                where purpose='REGISTER' and challenge_id=? and consumed_at is null and invalidated_at is null
                and expires_at>? and failed_attempts<?
                """, id.toString(), Timestamp.from(now), maxFailures) == 1;
    }

    /** Consuming is a state transition, not proof of code verification; the service verifies first. */
    public boolean consume(UUID id, Instant now, int maxFailures) {
        return jdbc.update("""
                update registration_challenge set consumed_at=?
                where purpose='REGISTER' and challenge_id=? and consumed_at is null and invalidated_at is null
                and expires_at>? and failed_attempts<?
                """, Timestamp.from(now), id.toString(), Timestamp.from(now), maxFailures) == 1;
    }

    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
