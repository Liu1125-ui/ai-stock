package com.aistock.user.admin;

import com.aistock.common.BusinessException;
import com.aistock.user.account.*;
import com.aistock.user.session.SessionService;
import com.aistock.user.support.AuthDatabaseFixture;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("mysql") class AdminUserServiceTest {
    @Test void disableRevokesSessionsAndEnableDoesNotReviveThem(){
        var db=new AuthDatabaseFixture();var admin=admin(db);var member=member(db);
        var service=new AdminUserService(db.jdbc(),db.clock());var sessions=new SessionService(db.jdbc(),new byte[32],db.clock());
        var issued=sessions.login("member@example.com","Long-test-password-1","test");
        assertThrows(BusinessException.class,()->service.setStatus(member,member,"DISABLED",0,"trace"));
        assertThrows(BusinessException.class,()->service.setStatus(admin,admin,"DISABLED",0,"trace"));
        assertThrows(BusinessException.class,()->service.setStatus(admin,member,"UNKNOWN",0,"trace"));
        assertEquals(1,service.setStatus(admin,member,"DISABLED",0,"trace").revision());
        assertThrows(BusinessException.class,()->sessions.resolve(issued.rawToken()));
        assertEquals("REVISION_CONFLICT",assertThrows(BusinessException.class,()->service.setStatus(admin,member,"ENABLED",0,"trace")).code());
        service.setStatus(admin,member,"ENABLED",1,"trace");
        assertThrows(BusinessException.class,()->sessions.resolve(issued.rawToken()));
        assertNotNull(sessions.login("member@example.com","Long-test-password-1","test"));
        assertEquals(2,db.jdbc().queryForObject("select count(*) from user_audit where action='ACCOUNT_STATUS'",Integer.class));
    }
    @Test void experimentGrantIsIdempotentAndKeepsHistory(){
        var db=new AuthDatabaseFixture();var admin=admin(db);var member=member(db);var service=new AdminUserService(db.jdbc(),db.clock());
        assertTrue(service.setExperimentGrant(admin,member,true,0,"trace").experimentAllowed());
        assertEquals(1,service.setExperimentGrant(admin,member,true,1,"trace").revision());
        assertEquals(1,db.jdbc().queryForObject("select count(*) from experiment_grant where state='ACTIVE'",Integer.class));
        service.setExperimentGrant(admin,member,false,1,"trace");
        assertEquals(1,db.jdbc().queryForObject("select count(*) from experiment_grant where state='REVOKED'",Integer.class));
        assertEquals(0,db.jdbc().queryForObject("select count(*) from experiment_grant where state='ACTIVE'",Integer.class));
        assertEquals(2,db.jdbc().queryForObject("select count(*) from user_audit where action='EXPERIMENT_GRANT'",Integer.class));
    }
    @Test void listUsesSafeBoundedFiltersAndCurrentAdministratorIdentity(){
        var db=new AuthDatabaseFixture();var admin=admin(db);var member=member(db);var service=new AdminUserService(db.jdbc(),db.clock());
        assertThrows(BusinessException.class,()->service.list(member,"",null,1,20));
        assertEquals(1,service.list(admin,"member",null,1,20).total());
        assertEquals(0,service.list(admin,"%' OR 1=1 --",null,1,20).total());
        assertThrows(BusinessException.class,()->service.list(admin,"",null,1,101));
        db.jdbc().update("update user_account set status='DISABLED' where user_id=?",admin.toString());
        assertThrows(BusinessException.class,()->service.list(admin,"",null,1,20));
    }
    static UUID admin(AuthDatabaseFixture db){return new AdminBootstrapCommand(db.jdbc(),db.clock()).initialize("admin@example.com","Long-admin-password-1","管理员","test-bootstrap");}
    static UUID member(AuthDatabaseFixture db){var id=UUID.randomUUID();new UserRepository(db.jdbc()).insertVerifiedOrdinaryUser(id,"member@example.com",new PasswordHasher().encode("Long-test-password-1"),"普通用户",db.clock().instant());return id;}
}
