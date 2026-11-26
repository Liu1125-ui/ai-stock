package com.aistock.user.admin;
import com.aistock.common.BusinessException;
import com.aistock.user.account.*;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Called only by controlled local bootstrap mode, never exposed through HTTP. */
public final class AdminBootstrapCommand {
    private final JdbcTemplate jdbc;private final Clock clock;private final TransactionTemplate tx;
    public AdminBootstrapCommand(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;tx=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));}
    public UUID initialize(String email,String password,String nickname,String trace){
        String normalized=EmailAddress.normalize(email);String stored=UserRepository.validateNickname(nickname);String hash=new PasswordHasher().encode(password);
        return tx.execute(status->{
            jdbc.queryForObject("select guard_key from bootstrap_guard where guard_key='ADMIN_INIT' for update",String.class);
            if(jdbc.queryForObject("select count(*) from user_account where role_code='ADMIN'",Long.class)>0)
                throw new BusinessException("ADMIN_ALREADY_INITIALIZED","管理员已初始化",HttpStatus.CONFLICT);
            if(jdbc.queryForObject("select count(*) from user_account where email_normalized=?",Long.class,normalized)>0)
                throw new BusinessException("REQUEST_CONFLICT","该邮箱已用于其他账号",HttpStatus.CONFLICT);
            var id=UUID.randomUUID();var now=clock.instant();
            jdbc.update("insert into user_account(user_id,email,email_normalized,email_verified_at,password_hash,role_code,status,nickname,created_at) values(?,?,?,?,?,'ADMIN','ENABLED',?,?)",
                    id.toString(),normalized,normalized,Timestamp.from(now),hash,stored,Timestamp.from(now));
            new AuditRepository(jdbc).success(null,id,"ADMIN_BOOTSTRAP",trace,now);return id;
        });
    }
}
