package com.aistock.user.session;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class SessionRepository {
    private final JdbcTemplate jdbc;
    public SessionRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public void create(UUID id,UUID userId,String tokenHash,long version,Instant now,Instant expiry){
        jdbc.update("insert into login_session(session_id,user_id,token_hash,credential_version,created_at,expires_at) values(?,?,?,?,?,?)",
                id.toString(),userId.toString(),tokenHash,version,Timestamp.from(now),Timestamp.from(expiry));
    }
    public Optional<Identity> resolve(String tokenHash,Instant now){
        return jdbc.query("""
                select u.user_id,u.role_code,u.credential_version,
                exists(select 1 from experiment_grant g where g.user_id=u.user_id and g.state='ACTIVE') experiment_allowed
                from login_session s join user_account u on u.user_id=s.user_id
                where s.token_hash=? and s.revoked_at is null and s.expires_at>? and u.status='ENABLED'
                and s.credential_version=u.credential_version
                """,(rs,n)->new Identity(UUID.fromString(rs.getString("user_id")),rs.getString("role_code"),rs.getBoolean("experiment_allowed"),rs.getLong("credential_version")),tokenHash,Timestamp.from(now)).stream().findFirst();
    }
    public void revoke(String hash,Instant now){jdbc.update("update login_session set revoked_at=? where token_hash=? and revoked_at is null",Timestamp.from(now),hash);}
    public void revokeAll(UUID userId,Instant now){jdbc.update("update login_session set revoked_at=? where user_id=? and revoked_at is null",Timestamp.from(now),userId.toString());}
    public boolean experimentAllowed(UUID id){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from experiment_grant where user_id=? and state='ACTIVE')",Boolean.class,id.toString()));}
}
