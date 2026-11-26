package com.aistock.user.registration;
import com.aistock.common.BusinessException;
import com.aistock.user.account.EmailAddress;
import com.aistock.user.account.SecretDigests;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class RateLimitService {
    public record Policy(int emailSendLimit, int sourceSendLimit, int emailLoginFailures, int sourceLoginFailures, long sendCooldownSeconds) {
        public Policy {
            if (emailSendLimit < 1 || sourceSendLimit < 1 || emailLoginFailures < 1 || sourceLoginFailures < 1 || sendCooldownSeconds < 1) throw new IllegalArgumentException("Rate limits must be positive");
        }
        public static Policy defaults() { return new Policy(5, 30, 10, 100, 60); }
    }
    private record Limit(String key, String scope, int max, long window, long cooldown) { }
    private record Pending(RateBucketRepository.Bucket bucket) { }
    private final RateBucketRepository buckets;
    private final SecretDigests digests;
    private final TransactionTemplate transaction;
    private final TransactionTemplate invalidSourceTransaction;
    private final Policy policy;
    public RateLimitService(JdbcTemplate jdbc, byte[] key) { this(jdbc, key, Policy.defaults()); }
    public RateLimitService(JdbcTemplate jdbc, byte[] key, Policy policy) {
        buckets = new RateBucketRepository(jdbc);
        digests = new SecretDigests(key);
        var manager = new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource()));
        transaction = new TransactionTemplate(manager);
        invalidSourceTransaction = new TransactionTemplate(manager);
        invalidSourceTransaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.policy = Objects.requireNonNull(policy);
    }
    public void reserveRegistration(String email, String source, Instant now) {
        requireSource(source);
        String normalized = normalizeOrCountSource(email, source, now, "REGISTER_SOURCE", policy.sourceSendLimit(), 3600);
        reserve(List.of(limit("REGISTER_EMAIL", normalized, policy.emailSendLimit(), 3600, policy.sendCooldownSeconds()), limit("REGISTER_SOURCE", source, policy.sourceSendLimit(), 3600, 0)), now);
    }
    public void reserveEmailChange(String email,String owner,String source,Instant now) {
        requireSource(source);
        reserve(List.of(limit("REGISTER_EMAIL",email,policy.emailSendLimit(),3600,policy.sendCooldownSeconds()),
                limit("REGISTER_SOURCE",source,policy.sourceSendLimit(),3600,0),
                limit("EMAIL_CHANGE_OWNER",owner,policy.emailSendLimit(),3600,policy.sendCooldownSeconds())),now);
    }
    public void recordLoginFailure(String email, String source, Instant now) {
        requireSource(source);
        String normalized = normalizeOrCountSource(email, source, now, "LOGIN_SOURCE", policy.sourceLoginFailures(), 900);
        reserve(List.of(limit("LOGIN_PAIR", normalized + "\u0000" + source, policy.emailLoginFailures(), 900, 0), limit("LOGIN_SOURCE", source, policy.sourceLoginFailures(), 900, 0)), now);
    }
    public void checkLoginAllowed(String email,String source,Instant now) {
        String normalized=EmailAddress.normalize(email); requireSource(source);
        var checks=List.of(limit("LOGIN_PAIR",normalized+"\u0000"+source,policy.emailLoginFailures(),900,0),limit("LOGIN_SOURCE",source,policy.sourceLoginFailures(),900,0));
        transaction.executeWithoutResult(status->{
            for(var limit:checks.stream().sorted(Comparator.comparing(Limit::key)).toList()) {
                var bucket=buckets.lock(limit.key(),limit.scope(),now,limit.window());
                if(now.isBefore(bucket.endsAt())&&bucket.count()>=limit.max()) throw limited();
            }
        });
    }
    public void lockRegistrationEmail(String email,Instant now) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Email serialization lock requires transaction");
        String normalized=EmailAddress.normalize(email);
        buckets.lock(digests.scope("REGISTER_EMAIL",normalized),"REGISTER_EMAIL",now,3600);
    }
    public void limitChallengeStatus(String source,Instant now) {
        requireSource(source);
        reserve(List.of(limit("CHALLENGE_STATUS",source,300,60,0)),now);
    }
    public void limitRegistrationSubmission(String source,Instant now){
        requireSource(source);reserve(List.of(limit("REGISTER_SUBMIT",source,30,60,0)),now);
    }
    private Limit limit(String scope, String subject, int max, long window, long cooldown) { return new Limit(digests.scope(scope, subject), scope, max, window, cooldown); }
    private String normalizeOrCountSource(String email, String source, Instant now, String scope, int max, long window) {
        try { return EmailAddress.normalize(email); }
        catch (BusinessException invalid) {
            // Persist source-only usage independently of the caller's validation-error rollback.
            reserve(List.of(limit(scope, source, max, window, 0)), now, invalidSourceTransaction);
            throw invalid;
        }
    }
    private void reserve(List<Limit> limits, Instant now) {
        reserve(limits, now, transaction);
    }
    private void reserve(List<Limit> limits, Instant now, TransactionTemplate selectedTransaction) {
        Objects.requireNonNull(now);
        selectedTransaction.executeWithoutResult(status -> {
            var pending = new ArrayList<Pending>();
            for (Limit limit : limits.stream().sorted(Comparator.comparing(Limit::key)).toList()) {
                var bucket = buckets.lock(limit.key(), limit.scope(), now, limit.window());
                boolean reset = !now.isBefore(bucket.endsAt());
                int count = reset ? 0 : bucket.count();
                if (count >= limit.max() || (bucket.lastAt() != null && now.isBefore(bucket.lastAt().plusSeconds(limit.cooldown())))) throw limited();
                pending.add(new Pending(reset ? new RateBucketRepository.Bucket(bucket.key(), now, now.plusSeconds(limit.window()), 0, bucket.lastAt()) : bucket));
            }
            for (Pending item : pending) {
                var bucket = item.bucket();
                buckets.write(bucket.key(), bucket.startedAt(), bucket.endsAt(), bucket.count() + 1, now);
            }
        });
    }
    private static void requireSource(String source) {
        if (source == null || source.isBlank() || source.length() > 128 || source.chars().anyMatch(Character::isISOControl)) throw new BusinessException("VALIDATION_FAILED", "来源地址无效", HttpStatus.BAD_REQUEST);
    }
    private static BusinessException limited() { return new BusinessException("RATE_LIMITED", "操作较频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS); }
}
