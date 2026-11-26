package com.aistock.user.session;
import com.aistock.common.BusinessException;
import com.aistock.user.account.*;
import com.aistock.user.registration.RateLimitService;
import java.sql.Timestamp;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class SessionService {
    private record Credential(UUID id,String hash,long version){@Override public String toString(){return "Credential[id="+id+"]";}}
    private static final String DUMMY_HASH=new PasswordHasher().encode("Equivalent-dummy-password-1");
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final SessionRepository repository;
    private final RateLimitService limits;
    private final SecretDigests digests;
    private final PasswordHasher passwords=new PasswordHasher();
    private final SecureRandom random=new SecureRandom();
    private final Clock clock;
    private final TransactionTemplate transaction;
    public SessionService(JdbcTemplate jdbc,byte[] key,Clock clock){
        this.jdbc=jdbc;this.clock=clock; users=new UserRepository(jdbc);repository=new SessionRepository(jdbc);limits=new RateLimitService(jdbc,key);digests=new SecretDigests(key);
        transaction=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    }
    public IssuedSession login(String suppliedEmail,String password,String source){
        String email;
        try{email=EmailAddress.normalize(suppliedEmail);}
        catch(BusinessException invalid){
            try{limits.recordLoginFailure(suppliedEmail,source,clock.instant());}
            catch(BusinessException error){if("RATE_LIMITED".equals(error.code()))throw error;}
            throw failed();
        }
        limits.checkLoginAllowed(email,source,clock.instant());
        var credential=jdbc.query("select user_id,password_hash,credential_version from user_account where email_normalized=?",
                (rs,n)->new Credential(UUID.fromString(rs.getString("user_id")),rs.getString("password_hash"),rs.getLong("credential_version")),email).stream().findFirst().orElse(null);
        boolean matches=passwords.matches(password,credential==null?DUMMY_HASH:credential.hash());
        if(!matches||credential==null){limits.recordLoginFailure(email,source,clock.instant());throw failed();}
        var issued=transaction.execute(status->{
            var current=users.lockById(credential.id()).orElse(null);
            if(current==null||!"ENABLED".equals(current.status())||current.credentialVersion()!=credential.version())return null;
            String currentHash=jdbc.queryForObject("select password_hash from user_account where user_id=?",String.class,credential.id().toString());
            if(!credential.hash().equals(currentHash))return null;
            byte[] bytes=new byte[32];random.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            var now=clock.instant();var expiry=now.plusSeconds(8*3600);
            repository.create(UUID.randomUUID(),current.userId(),digests.session(token),current.credentialVersion(),now,expiry);
            jdbc.update("update user_account set last_active_at=? where user_id=?",Timestamp.from(now),current.userId().toString());
            return new IssuedSession(token,expiry,new Identity(current.userId(),current.role(),repository.experimentAllowed(current.userId()),current.credentialVersion()));
        });
        if(issued==null){limits.recordLoginFailure(email,source,clock.instant());throw failed();}
        return issued;
    }
    public Identity resolve(String token){
        if(!validToken(token))throw authRequired();
        return repository.resolve(digests.session(token),clock.instant()).orElseThrow(this::authRequired);
    }
    public void logout(String token){if(validToken(token))repository.revoke(digests.session(token),clock.instant());}
    private boolean validToken(String token){return token!=null&&token.matches("[A-Za-z0-9_-]{43}");}
    private BusinessException failed(){return new BusinessException("LOGIN_FAILED","邮箱或密码错误，或账号暂不可用",HttpStatus.UNAUTHORIZED);}
    private BusinessException authRequired(){return new BusinessException("AUTH_REQUIRED","请重新登录",HttpStatus.UNAUTHORIZED);}
}
