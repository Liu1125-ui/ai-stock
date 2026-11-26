package com.aistock.user.registration;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class RegistrationDispatchRepository {
    public record View(UUID requestId,String state,Instant expiresAt) { }
    public record Claim(UUID requestId,long version,int attempt,Instant expiresAt,RegistrationPayloadCodec.Payload payload) { }
    private final JdbcTemplate jdbc;
    public RegistrationDispatchRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void save(MailDeliveryClient.Command command,RegistrationPayloadCodec.Payload payload,Instant now) {
        jdbc.update("""
                insert into registration_dispatch(request_id,challenge_id,state,next_attempt_at,
                  payload_ciphertext,payload_nonce,payload_key_id,expires_at,created_at,updated_at)
                values (?,?,?,?,?,?,?,?,?,?)
                """,command.requestId().toString(),command.challengeId().toString(),"PENDING",Timestamp.from(now),
                payload.ciphertext(),payload.nonce(),payload.keyId(),Timestamp.from(command.expiresAt()),Timestamp.from(now),Timestamp.from(now));
    }
    public Optional<View> find(UUID challengeId) {
        return jdbc.query("select request_id,state,expires_at from registration_dispatch where challenge_id=?",
                (rs,n)->new View(UUID.fromString(rs.getString("request_id")),rs.getString("state"),rs.getTimestamp("expires_at").toInstant()),challengeId.toString()).stream().findFirst();
    }
    public void expire(Instant now) {
        jdbc.update("""
                update registration_dispatch set state='EXPIRED',payload_ciphertext=null,payload_nonce=null,payload_key_id=null,lease_until=null,updated_at=?
                where state in ('PENDING','CLAIMED','FAILED') and expires_at<=?
                """,Timestamp.from(now),Timestamp.from(now));
    }
    public Optional<Claim> claim(Instant now) {
        var rows=jdbc.query("""
                select request_id,claim_version,attempt_count,expires_at,payload_ciphertext,payload_nonce,payload_key_id
                from registration_dispatch where expires_at>? and
                ((state='PENDING' and next_attempt_at<=?) or (state='CLAIMED' and lease_until<=?))
                order by created_at,request_id limit 1 for update skip locked
                """,(rs,n)->new Claim(UUID.fromString(rs.getString("request_id")),rs.getLong("claim_version")+1,rs.getInt("attempt_count")+1,rs.getTimestamp("expires_at").toInstant(),
                new RegistrationPayloadCodec.Payload(rs.getBytes("payload_ciphertext"),rs.getBytes("payload_nonce"),rs.getString("payload_key_id"))),Timestamp.from(now),Timestamp.from(now),Timestamp.from(now));
        if(rows.isEmpty()) return Optional.empty();
        var claim=rows.get(0);
        jdbc.update("update registration_dispatch set state='CLAIMED',attempt_count=?,claim_version=?,lease_until=?,updated_at=? where request_id=?",
                claim.attempt(),claim.version(),Timestamp.from(now.plusSeconds(30)),Timestamp.from(now),claim.requestId().toString());
        return Optional.of(claim);
    }
    public boolean finish(Claim claim,String state,Instant next,String error,Instant now) {
        boolean clear="ACCEPTED".equals(state)||"EXPIRED".equals(state);
        return jdbc.update("""
                update registration_dispatch set state=?,next_attempt_at=?,last_error_code=?,lease_until=null,updated_at=?,
                  payload_ciphertext=if(?,null,payload_ciphertext),payload_nonce=if(?,null,payload_nonce),payload_key_id=if(?,null,payload_key_id)
                where request_id=? and state='CLAIMED' and claim_version=?
                """,state,Timestamp.from(next),error,Timestamp.from(now),clear,clear,clear,claim.requestId().toString(),claim.version())==1;
    }
}
