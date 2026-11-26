package com.aistock.user.admin;
import com.aistock.common.BusinessException;
import com.aistock.user.account.*;
import com.aistock.user.session.SessionRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class AdminUserService {
    public record UserPage(List<ProfileView> items,long total,int page,int size){}
    private final JdbcTemplate jdbc;private final Clock clock;private final UserRepository users;
    private final ProfileService profiles;private final SessionRepository sessions;private final TransactionTemplate tx;private final AuditRepository audit;
    public AdminUserService(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;users=new UserRepository(jdbc);profiles=new ProfileService(jdbc,clock);sessions=new SessionRepository(jdbc);audit=new AuditRepository(jdbc);tx=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));}
    public UserPage list(UUID operator,String query,String state,int page,int size){
        requireAdmin(operator,false);
        String q=query==null?"":query.strip();
        if(q.length()>254||page<1||size<1||size>100||(state!=null&&!Set.of("ENABLED","DISABLED").contains(state)))throw invalid();
        String pattern="%"+q.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        String where=" where role_code='USER' and (email_normalized like ? escape '!' or nickname like ? escape '!')"+(state==null?"":" and status=?");
        var params=new ArrayList<Object>(List.of(pattern,pattern));if(state!=null)params.add(state);
        long total=jdbc.queryForObject("select count(*) from user_account"+where,Long.class,params.toArray());
        params.add(size);params.add(((long)page-1)*size);
        var items=jdbc.query("select user_id,email_normalized,nickname,role_code,status,revision,exists(select 1 from experiment_grant g where g.user_id=user_account.user_id and g.state='ACTIVE') experiment_allowed from user_account"+where+" order by created_at desc,user_id limit ? offset ?",
                (r,n)->new ProfileView(UUID.fromString(r.getString("user_id")),r.getString("email_normalized"),r.getString("nickname"),r.getString("role_code"),r.getString("status"),true,r.getBoolean("experiment_allowed"),r.getLong("revision")),params.toArray());
        return new UserPage(List.copyOf(items),total,page,size);
    }
    public ProfileView setStatus(UUID operator,UUID target,String desired,long revision,String trace){
        if(desired==null||!Set.of("ENABLED","DISABLED").contains(desired))throw invalid();
        return tx.execute(status->{requireAdmin(operator,true);var account=lockOrdinary(target);ProfileService.checkRevision(account,revision);
            if(account.status().equals(desired))return profiles.view(account);
            jdbc.update("update user_account set status=?,credential_version=credential_version+1,revision=revision+1 where user_id=?",desired,target.toString());
            sessions.revokeAll(target,clock.instant());audit.success(operator,target,"ACCOUNT_STATUS",trace,clock.instant());
            return profiles.view(users.findById(target).orElseThrow());});
    }
    public ProfileView setExperimentGrant(UUID operator,UUID target,boolean enabled,long revision,String trace){
        return tx.execute(status->{requireAdmin(operator,true);var account=lockOrdinary(target);ProfileService.checkRevision(account,revision);
            boolean active=sessions.experimentAllowed(target);if(active==enabled)return profiles.view(account);
            if(enabled)jdbc.update("insert into experiment_grant(grant_id,user_id,granted_by,state,granted_at) values(?,?,?,'ACTIVE',?)",UUID.randomUUID().toString(),target.toString(),operator.toString(),Timestamp.from(clock.instant()));
            else jdbc.update("update experiment_grant set state='REVOKED',revoked_at=? where user_id=? and state='ACTIVE'",Timestamp.from(clock.instant()),target.toString());
            jdbc.update("update user_account set revision=revision+1 where user_id=?",target.toString());
            audit.success(operator,target,"EXPERIMENT_GRANT",trace,clock.instant());return profiles.view(users.findById(target).orElseThrow());});
    }
    private void requireAdmin(UUID id,boolean lock){
        var account=lock?users.lockById(id):users.findById(id);
        if(account.filter(a->a.role().equals("ADMIN")&&a.status().equals("ENABLED")).isEmpty())throw new BusinessException("ACCESS_DENIED","需要管理员权限",HttpStatus.FORBIDDEN);
    }
    private UserRepository.Account lockOrdinary(UUID id){
        var account=users.lockById(id).orElseThrow(()->new BusinessException("RESOURCE_NOT_FOUND","账号不存在",HttpStatus.NOT_FOUND));
        if(!account.role().equals("USER"))throw new BusinessException("ACCESS_DENIED","当前操作仅适用于普通用户",HttpStatus.FORBIDDEN);
        return account;
    }
    private static BusinessException invalid(){return new BusinessException("VALIDATION_FAILED","筛选条件或账号状态不正确",HttpStatus.BAD_REQUEST);}
}
