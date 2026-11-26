package com.aistock.user.registration;

import com.aistock.common.BusinessException;
import com.aistock.user.account.*;
import java.sql.Timestamp;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class RegistrationService {
    private record Outcome(RegistrationResult result, String error) { }
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final ChallengeRepository challenges;
    private final RegistrationReceiptRepository receipts;
    private final RegistrationDispatchRepository dispatch;
    private final RateLimitService limits;
    private final SecretDigests digests;
    private final RegistrationPayloadCodec payloads;
    private final PasswordHasher passwords;
    private final SecureRandom random=new SecureRandom();
    private final Clock clock;
    private final TransactionTemplate transaction;
    private MailDeliveryClient deliveryClient;
    private String serviceToken;
    public RegistrationService(JdbcTemplate jdbc,byte[] digestKey,byte[] payloadKey,Clock clock) {
        this(jdbc,digestKey,payloadKey,clock,new PasswordHasher());
    }
    RegistrationService(JdbcTemplate jdbc,byte[] digestKey,byte[] payloadKey,Clock clock,PasswordHasher passwords) {
        this.passwords=passwords;
        this.jdbc=jdbc; this.clock=clock; users=new UserRepository(jdbc); challenges=new ChallengeRepository(jdbc);
        receipts=new RegistrationReceiptRepository(jdbc); dispatch=new RegistrationDispatchRepository(jdbc);
        limits=new RateLimitService(jdbc,digestKey); digests=new SecretDigests(digestKey); payloads=new RegistrationPayloadCodec(payloadKey);
        transaction=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    }
    public RegistrationService(JdbcTemplate jdbc,byte[] digestKey,byte[] payloadKey,Clock clock,MailDeliveryClient client,String token) {
        this(jdbc,digestKey,payloadKey,clock); this.deliveryClient=client; this.serviceToken=token;
    }
    public ChallengeView request(String email,String source) {
        // Invalid formats count only a source bucket in a committed transaction.
        try { EmailAddress.normalize(email); }
        catch(BusinessException invalid) { limits.reserveRegistration(email,source,clock.instant()); throw invalid; }
        String normalized=EmailAddress.normalize(email); Instant now=clock.instant();
        return transaction.execute(status->{
            limits.reserveRegistration(normalized,source,now);
            invalidateOther(normalized,null,now);
            boolean decoy=users.findByEmail(normalized).isPresent();
            UUID id=UUID.randomUUID(); Instant expiry=now.plusSeconds(600);
            String code=String.format(java.util.Locale.ROOT,"%06d",random.nextInt(1_000_000));
            challenges.create(id,normalized,digests.code(id,normalized,code),"v1",now,expiry);
            if(decoy) jdbc.update("update registration_challenge set invalidated_at=? where challenge_id=?",Timestamp.from(now),id.toString());
            var command=new MailDeliveryClient.Command(UUID.randomUUID(),id,normalized,code,expiry);
            dispatch.save(command,payloads.encrypt(command),now);
            return new ChallengeView(id,expiry,now.plusSeconds(60),"PENDING");
        });
    }
    public RegistrationResult register(RegisterCommand command) {
        return register(command,"local-registration");
    }
    public RegistrationResult register(RegisterCommand command,String source) {
        limits.limitRegistrationSubmission(source,clock.instant());
        if(command==null||command.requestId()==null||command.challengeId()==null||command.code()==null||!command.code().matches("[0-9]{6}")) throw invalid();
        String email=EmailAddress.normalize(command.email()); String nickname=UserRepository.validateNickname(command.nickname());
        String digest=digests.scope("REGISTER_RECEIPT",command.challengeId()+"\n"+email+"\n"+nickname);
        var snapshot=challenges.findById(command.challengeId()).orElse(null);
        boolean eligible=snapshot!=null&&snapshot.email().equals(email)&&snapshot.invalidatedAt()==null&&snapshot.consumedAt()==null
                &&clock.instant().isBefore(snapshot.expiresAt())&&snapshot.failedAttempts()<5&&"v1".equals(snapshot.keyId());
        if(!eligible&&receipts.find(command.requestId()).isEmpty())throw invalid();
        // Wrong codes still enter the short transaction to persist failure counts, without BCrypt work.
        String passwordHash=eligible&&digests.matchesCode(command.challengeId(),email,command.code(),snapshot.digest())?passwords.encode(command.password()):null;
        Instant lockStartedAt=clock.instant();
        Outcome outcome;
        try {
            outcome=transaction.execute(status->{
                limits.lockRegistrationEmail(email,lockStartedAt);
                var challenge=challenges.lockById(command.challengeId()).orElse(null);
                Instant now=clock.instant();
                var replay=receipts.find(command.requestId());
                if(replay.isPresent()) {
                    if(!replay.get().challengeId().equals(command.challengeId())||!replay.get().digest().equals(digest)) return new Outcome(null,"REQUEST_CONFLICT");
                    return new Outcome(new RegistrationResult(replay.get().userId(),true),null);
                }
                if(challenge==null||!challenge.email().equals(email)||challenge.invalidatedAt()!=null||challenge.consumedAt()!=null
                        ||!now.isBefore(challenge.expiresAt())||challenge.failedAttempts()>=5||!"v1".equals(challenge.keyId())) return new Outcome(null,"CHALLENGE_INVALID");
                if(!digests.matchesCode(command.challengeId(),email,command.code(),challenge.digest())) {
                    challenges.recordFailedAttempt(command.challengeId(),now,5);
                    return new Outcome(null,"CHALLENGE_INVALID");
                }
                if(users.findByEmail(email).isPresent()) return new Outcome(null,"CHALLENGE_INVALID");
                if(passwordHash==null)return new Outcome(null,"CHALLENGE_INVALID");
                UUID userId=UUID.randomUUID();
                users.insertVerifiedOrdinaryUser(userId,email,passwordHash,nickname,now);
                if(!challenges.consume(command.challengeId(),now,5)) throw new IllegalStateException("Locked registration challenge changed");
                receipts.save(command.requestId(),command.challengeId(),userId,digest,now);
                invalidateOther(email,command.challengeId(),now);
                return new Outcome(new RegistrationResult(userId,false),null);
            });
        } catch(org.springframework.dao.DataIntegrityViolationException conflict) { throw new BusinessException("REQUEST_CONFLICT","请求已处理或账号已存在",HttpStatus.CONFLICT); }
        if(outcome.error()!=null) {
            if("REQUEST_CONFLICT".equals(outcome.error())) throw new BusinessException("REQUEST_CONFLICT","请求标识与原提交不一致",HttpStatus.CONFLICT);
            throw invalid();
        }
        return outcome.result();
    }
    public ChallengeView status(UUID id) {
        var challenge=challenges.findById(id).orElseThrow(()->new BusinessException("RESOURCE_NOT_FOUND","验证请求不存在",HttpStatus.NOT_FOUND));
        return deliveryStatus(id,challenge.expiresAt());
    }
    ChallengeView deliveryStatus(UUID id,Instant expiry) {
        var delivery=dispatch.find(id).orElseThrow();
        String state=clock.instant().isBefore(expiry)?delivery.state():"EXPIRED";
        if("ACCEPTED".equals(state)&&deliveryClient!=null) {
            try {
                var response=deliveryClient.find(delivery.requestId(),serviceToken);
                if(response==null||response.data()==null||!"OK".equals(response.code())) throw new IllegalStateException("Invalid delivery status");
                if(java.util.Set.of("SENT","FAILED","EXPIRED").contains(response.data().state()))state=response.data().state();
            } catch(RuntimeException unavailable) { throw new BusinessException("EMAIL_SERVICE_UNAVAILABLE","暂时无法查询邮件状态，请稍后重试",HttpStatus.SERVICE_UNAVAILABLE); }
        }
        return new ChallengeView(id,expiry,expiry.minusSeconds(540),state);
    }
    public ChallengeView status(UUID id,String source) {
        limits.limitChallengeStatus(source,clock.instant());
        return status(id);
    }
    private void invalidateOther(String email,UUID except,Instant now) {
        jdbc.update("update registration_challenge set invalidated_at=? where purpose='REGISTER' and email_normalized=? and consumed_at is null and invalidated_at is null and challenge_id<>?",
                Timestamp.from(now),email,except==null?"":except.toString());
        jdbc.update("""
                update registration_dispatch d join registration_challenge c on c.challenge_id=d.challenge_id
                set d.state='EXPIRED',d.payload_ciphertext=null,d.payload_nonce=null,d.payload_key_id=null,d.lease_until=null,d.updated_at=?
                where c.purpose='REGISTER' and c.email_normalized=? and c.invalidated_at is not null and d.state in ('PENDING','CLAIMED','FAILED')
                """,Timestamp.from(now),email);
    }
    private BusinessException invalid() { return new BusinessException("CHALLENGE_INVALID","验证码无效或已失效，请重新申请",HttpStatus.BAD_REQUEST); }
}
