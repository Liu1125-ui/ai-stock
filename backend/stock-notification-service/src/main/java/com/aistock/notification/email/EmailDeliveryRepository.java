package com.aistock.notification.email;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class EmailDeliveryRepository {
    public record Row(DeliveryView view, String digest) { }
    public record Claim(UUID requestId, long version, int attempt, Instant expiresAt, MailPayloadCodec.Encrypted payload) { }
    private final JdbcTemplate jdbc;
    public EmailDeliveryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<Row> find(UUID id) {
        return jdbc.query("select request_id,state,expires_at,payload_digest from email_delivery where request_id=?", (rs, n) ->
                new Row(new DeliveryView(UUID.fromString(rs.getString("request_id")), rs.getString("state"), rs.getTimestamp("expires_at").toInstant()), rs.getString("payload_digest")), id.toString()).stream().findFirst();
    }
    public void insert(EmailCommand command, String digest, MailPayloadCodec.Encrypted payload, Instant now) {
        jdbc.update("""
                insert into email_delivery(delivery_id,request_id,challenge_id,recipient_email,template_code,
                  state,next_attempt_at,payload_ciphertext,payload_nonce,payload_key_id,payload_digest,expires_at,created_at)
                values (?,?,?,?,'REGISTER_CODE','PENDING',?,?,?,?,?,?,?)
                on duplicate key update request_id=request_id
                """, UUID.randomUUID().toString(), command.requestId().toString(), command.challengeId().toString(), command.email(),
                Timestamp.from(now), payload.ciphertext(), payload.nonce(), payload.keyId(), digest, Timestamp.from(command.expiresAt()), Timestamp.from(now));
    }
    public void expire(Instant now) {
        jdbc.update("""
                update email_delivery set state='EXPIRED',payload_ciphertext=null,payload_nonce=null,payload_key_id=null,lease_until=null
                where state in ('PENDING','CLAIMED') and expires_at<=?
                """, Timestamp.from(now));
    }
    public Optional<Claim> claim(Instant now) {
        var rows = jdbc.query("""
                select request_id,claim_version,attempt_count,expires_at,payload_ciphertext,payload_nonce,payload_key_id
                from email_delivery where expires_at>? and
                ((state='PENDING' and next_attempt_at<=?) or (state='CLAIMED' and lease_until<=?))
                order by created_at,request_id limit 1 for update skip locked
                """, (rs, n) -> new Claim(UUID.fromString(rs.getString("request_id")), rs.getLong("claim_version") + 1,
                rs.getInt("attempt_count") + 1, rs.getTimestamp("expires_at").toInstant(),
                new MailPayloadCodec.Encrypted(rs.getBytes("payload_ciphertext"), rs.getBytes("payload_nonce"), rs.getString("payload_key_id"))),
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
        if (rows.isEmpty()) return Optional.empty();
        Claim claim = rows.get(0);
        jdbc.update("update email_delivery set state='CLAIMED',claim_version=?,attempt_count=?,lease_until=?,last_attempt_at=? where request_id=?",
                claim.version(), claim.attempt(), Timestamp.from(now.plusSeconds(30)), Timestamp.from(now), claim.requestId().toString());
        return Optional.of(claim);
    }
    public boolean finish(Claim claim, String state, Instant next, String error) {
        boolean terminal = !"PENDING".equals(state);
        return jdbc.update("""
                update email_delivery set state=?,next_attempt_at=?,last_error_code=?,lease_until=null,
                  payload_ciphertext=if(?,null,payload_ciphertext),payload_nonce=if(?,null,payload_nonce),payload_key_id=if(?,null,payload_key_id)
                where request_id=? and state='CLAIMED' and claim_version=?
                """, state, Timestamp.from(next), error, terminal, terminal, terminal, claim.requestId().toString(), claim.version()) == 1;
    }
}
