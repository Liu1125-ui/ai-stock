package com.aistock.user.registration;

import com.aistock.common.BusinessException;
import com.aistock.user.account.EmailAddress;
import com.aistock.user.account.PasswordHasher;
import com.aistock.user.account.SecretDigests;
import com.aistock.user.account.UserRepository;
import com.aistock.user.session.SessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Coordinates the account, its verification challenge, and its sessions in one database. */
public final class EmailChangeService {
    private record Challenge(String email,String digest,Instant expiry,long version,boolean open) {}
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final RegistrationPayloadCodec codec;
    private final RegistrationService delivery;
    private final RegistrationDispatchRepository dispatch;
    private final UserRepository users;
    private final SessionRepository sessions;
    private final SecretDigests digests;
    private final RateLimitService limits;
    private final TransactionTemplate tx;
    private final PasswordHasher passwords = new PasswordHasher();
    private final SecureRandom random = new SecureRandom();

    public EmailChangeService(JdbcTemplate jdbc,byte[] key,RegistrationPayloadCodec codec,Clock clock,RegistrationService delivery) {
        this.jdbc=jdbc; this.clock=clock; this.codec=codec; this.delivery=delivery;
        this.dispatch=new RegistrationDispatchRepository(jdbc); this.users=new UserRepository(jdbc);
        this.sessions=new SessionRepository(jdbc); this.digests=new SecretDigests(key);
        this.limits=new RateLimitService(jdbc,key);
        this.tx=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    }

    public ChallengeView request(UUID owner,long version,String target,String currentPassword,String source) {
        String email=EmailAddress.normalize(target);
        var account=users.findById(owner).filter(a->enabled(a,version)).orElseThrow(EmailChangeService::unauthorized);
        Instant startedAt=clock.instant();
        limits.checkLoginAllowed(account.email(),source,startedAt);
        String hash=jdbc.queryForObject("select password_hash from user_account where user_id=?",String.class,owner.toString());
        if(!passwords.matches(currentPassword,hash)) {
            limits.recordLoginFailure(account.email(),source,clock.instant());
            throw new BusinessException("PASSWORD_MISMATCH","当前密码不正确",HttpStatus.BAD_REQUEST);
        }
        return tx.execute(state->{
            limits.reserveEmailChange(email,owner.toString(),source,clock.instant());
            var locked=lockOwner(owner,version);
            if(locked.email().equals(email)||users.findByEmail(email).isPresent())throw unavailable();
            Instant now=clock.instant(), expiry=now.plusSeconds(600);
            invalidatePending(owner,now);
            UUID id=UUID.randomUUID();
            String code=String.format(Locale.ROOT,"%06d",random.nextInt(1_000_000));
            jdbc.update("""
                insert into registration_challenge(challenge_id,email_normalized,code_digest,digest_key_id,
                    purpose,created_at,expires_at,owner_user_id,credential_version)
                values (?,?,?,'v1','EMAIL_CHANGE',?,?,?,?)
                """,id.toString(),email,digest(id,owner,email,code),Timestamp.from(now),Timestamp.from(expiry),owner.toString(),version);
            var command=new MailDeliveryClient.Command(UUID.randomUUID(),id,email,code,expiry);
            dispatch.save(command,codec.encrypt(command),now);
            return new ChallengeView(id,expiry,now.plusSeconds(60),"PENDING");
        });
    }

    public ChallengeView status(UUID owner,long version,UUID id,String source) {
        limits.limitChallengeStatus(source,clock.instant());
        var challenge=lookup(owner,id,false);
        if(challenge==null||challenge.version()!=version)throw new BusinessException("RESOURCE_NOT_FOUND","验证请求不存在",HttpStatus.NOT_FOUND);
        if(!challenge.open())return new ChallengeView(id,challenge.expiry(),challenge.expiry().minusSeconds(540),"EXPIRED");
        return delivery.deliveryStatus(id,challenge.expiry());
    }

    public void confirm(UUID owner,long version,UUID id,String code,String source) {
        limits.limitRegistrationSubmission(source,clock.instant());
        if(id==null||code==null||!code.matches("[0-9]{6}"))throw invalid();
        boolean confirmed;
        try {
            confirmed=Boolean.TRUE.equals(tx.execute(state->{
                lockOwner(owner,version);
                var challenge=lookup(owner,id,true);
                Instant now=clock.instant();
                if(challenge==null||challenge.version()!=version||!challenge.open()||!now.isBefore(challenge.expiry()))return false;
                if(!MessageDigest.isEqual(ascii(challenge.digest()),ascii(digest(id,owner,challenge.email(),code)))) {
                    jdbc.update("update registration_challenge set failed_attempts=failed_attempts+1 where challenge_id=?",id.toString());
                    return false;
                }
                jdbc.update("""
                    update user_account set email=?,email_normalized=?,email_verified_at=?,email_version=email_version+1,
                    credential_version=credential_version+1,revision=revision+1 where user_id=?
                    """,challenge.email(),challenge.email(),Timestamp.from(now),owner.toString());
                jdbc.update("update registration_challenge set consumed_at=? where challenge_id=?",Timestamp.from(now),id.toString());
                invalidatePending(owner,now);
                sessions.revokeAll(owner,now);
                return true;
            }));
        } catch(DuplicateKeyException conflict) { throw unavailable(); }
        if(!confirmed)throw invalid();
    }

    private Challenge lookup(UUID owner,UUID id,boolean lock) {
        return jdbc.query("""
            select email_normalized,code_digest,expires_at,credential_version,
                consumed_at is null and invalidated_at is null and failed_attempts<5 and digest_key_id='v1' as is_open
            from registration_challenge where challenge_id=? and owner_user_id=? and purpose='EMAIL_CHANGE'
            """+(lock?" for update":""),(row,n)->new Challenge(row.getString(1),row.getString(2),
                row.getTimestamp(3).toInstant(),row.getLong(4),row.getBoolean(5)),id.toString(),owner.toString()).stream().findFirst().orElse(null);
    }
    private void invalidatePending(UUID owner,Instant now) {
        jdbc.update("""
            update registration_challenge set invalidated_at=? where owner_user_id=? and purpose='EMAIL_CHANGE'
            and consumed_at is null and invalidated_at is null
            """,Timestamp.from(now),owner.toString());
        jdbc.update("""
            update registration_dispatch d join registration_challenge c on c.challenge_id=d.challenge_id
            set d.state='EXPIRED',d.payload_ciphertext=null,d.payload_nonce=null,d.payload_key_id=null,d.lease_until=null,d.updated_at=?
            where c.owner_user_id=? and c.purpose='EMAIL_CHANGE'
            and (c.invalidated_at is not null or c.consumed_at is not null) and d.state in ('PENDING','CLAIMED','FAILED')
            """,Timestamp.from(now),owner.toString());
    }
    private UserRepository.Account lockOwner(UUID owner,long version) {
        return users.lockById(owner).filter(a->enabled(a,version)).orElseThrow(EmailChangeService::unauthorized);
    }
    private static boolean enabled(UserRepository.Account account,long version) {
        return "ENABLED".equals(account.status())&&account.credentialVersion()==version;
    }
    private String digest(UUID id,UUID owner,String email,String code) {
        return digests.scope("EMAIL_CHANGE_CODE",id+"\n"+owner+"\n"+email+"\n"+code);
    }
    private static byte[] ascii(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
    private static BusinessException invalid() { return new BusinessException("CHALLENGE_INVALID","验证码无效或已失效，请重新申请",HttpStatus.BAD_REQUEST); }
    private static BusinessException unauthorized() { return new BusinessException("AUTH_REQUIRED","请重新登录",HttpStatus.UNAUTHORIZED); }
    private static BusinessException unavailable() { return new BusinessException("EMAIL_UNAVAILABLE","此地址不能用于更换邮箱",HttpStatus.CONFLICT); }
}
