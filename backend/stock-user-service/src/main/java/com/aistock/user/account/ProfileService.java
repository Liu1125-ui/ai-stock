package com.aistock.user.account;

import com.aistock.common.BusinessException;
import com.aistock.user.session.SessionRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class ProfileService {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final SessionRepository sessions;
    private final TransactionTemplate tx;
    private final PasswordHasher passwords=new PasswordHasher();
    private final Clock clock;
    public ProfileService(JdbcTemplate jdbc,Clock clock){
        this.jdbc=jdbc;this.clock=clock;users=new UserRepository(jdbc);sessions=new SessionRepository(jdbc);
        tx=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    }
    public ProfileView me(UUID id){return view(users.findById(id).filter(a->a.status().equals("ENABLED")).orElseThrow(ProfileService::unauthorized));}
    public ProfileView rename(UUID id,String nickname,long revision){
        return update(id,nickname,revision,null);
    }
    private static final java.util.Set<String> AVATAR_STYLES=java.util.Set.of("ocean","jade","plum","amber");
    public ProfileView update(UUID id,String nickname,long revision,String avatarStyle){
        String stored=UserRepository.validateNickname(nickname);
        if(avatarStyle!=null&&!"default".equals(avatarStyle)&&!AVATAR_STYLES.contains(avatarStyle))
            throw new BusinessException("VALIDATION_FAILED","请选择有效的头像样式",HttpStatus.BAD_REQUEST);
        return tx.execute(status->{
            var account=lockEnabled(id);
            checkRevision(account,revision);
            users.rename(id,stored,revision);
            if(avatarStyle!=null)users.updateAvatar(id,"default".equals(avatarStyle)?null:avatarStyle);
            return view(users.findById(id).orElseThrow());
        });
    }
    public void changePassword(UUID id,String currentPassword,String newPassword){
        // BCrypt work stays outside the row lock; the hash is rechecked under lock before mutation.
        String previous=jdbc.query("select password_hash from user_account where user_id=?",(rs,n)->rs.getString(1),id.toString())
                .stream().findFirst().orElseThrow(ProfileService::unauthorized);
        if(!passwords.matches(currentPassword,previous))throw new BusinessException("PASSWORD_MISMATCH","当前密码不正确",HttpStatus.BAD_REQUEST);
        String encoded=passwords.encode(newPassword);
        tx.executeWithoutResult(status->{lockEnabled(id);
            String locked=jdbc.queryForObject("select password_hash from user_account where user_id=?",String.class,id.toString());
            if(!previous.equals(locked))throw new BusinessException("REVISION_CONFLICT","密码已发生变更，请重新登录",HttpStatus.CONFLICT);
            jdbc.update("update user_account set password_hash=?,credential_version=credential_version+1,revision=revision+1 where user_id=?",encoded,id.toString());
            sessions.revokeAll(id,clock.instant());});
    }
    private UserRepository.Account lockEnabled(UUID id){return users.lockById(id).filter(a->a.status().equals("ENABLED")).orElseThrow(ProfileService::unauthorized);}
    public ProfileView view(UserRepository.Account account){return new ProfileView(account.userId(),account.email(),account.nickname(),account.role(),account.status(),true,sessions.experimentAllowed(account.userId()),account.revision(),account.avatarStyle()!=null&&AVATAR_STYLES.contains(account.avatarStyle())?account.avatarStyle():null);}
    public static void checkRevision(UserRepository.Account account,long revision){if(revision<0||account.revision()!=revision)throw new BusinessException("REVISION_CONFLICT","资料已更新，请刷新后重试",HttpStatus.CONFLICT);}
    private static BusinessException unauthorized(){return new BusinessException("AUTH_REQUIRED","请重新登录",HttpStatus.UNAUTHORIZED);}
}
