package com.aistock.user.registration;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class RegistrationReceiptRepository {
    public record Receipt(UUID userId, UUID challengeId, String digest) { }
    private final JdbcTemplate jdbc;
    public RegistrationReceiptRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Optional<Receipt> find(UUID requestId) {
        return jdbc.query("select user_id,challenge_id,public_request_digest from registration_receipt where registration_request_id=?",
                (rs,n)->new Receipt(UUID.fromString(rs.getString("user_id")),UUID.fromString(rs.getString("challenge_id")),rs.getString("public_request_digest")),requestId.toString()).stream().findFirst();
    }
    public void save(UUID requestId, UUID challengeId, UUID userId, String digest, Instant now) {
        jdbc.update("insert into registration_receipt(registration_request_id,challenge_id,user_id,public_request_digest,created_at) values (?,?,?,?,?)",
                requestId.toString(),challengeId.toString(),userId.toString(),digest,Timestamp.from(now));
    }
}
