package com.aistock.user.session;
import com.aistock.common.BusinessException;
import com.aistock.user.account.PasswordHasher;
import com.aistock.user.account.UserRepository;
import com.aistock.user.support.AuthDatabaseFixture;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("mysql")
class SessionServiceTest {
    @Test void logoutRevokesCurrentSessionAndDatabaseNeverContainsRawToken() {
        var db=new AuthDatabaseFixture(); String email=UUID.randomUUID()+"@example.com";
        new UserRepository(db.jdbc()).insertVerifiedOrdinaryUser(UUID.randomUUID(),email,new PasswordHasher().encode("Long-test-password-1"),"测试用户",db.clock().instant());
        var sessions=new SessionService(db.jdbc(),new byte[32],db.clock());
        var issued=sessions.login(email,"Long-test-password-1","test-source");
        assertNotNull(sessions.resolve(issued.rawToken()).userId());
        assertFalse(issued.toString().contains(issued.rawToken()));
        assertNotEquals(issued.rawToken(),db.jdbc().queryForObject("select token_hash from login_session",String.class));
        sessions.logout(issued.rawToken()); sessions.logout(issued.rawToken());
        assertEquals("AUTH_REQUIRED",assertThrows(BusinessException.class,()->sessions.resolve(issued.rawToken())).code());
    }
    @Test void expiryAndDisabledAccountRejectOldSession() {
        var db=new AuthDatabaseFixture(); String email=UUID.randomUUID()+"@example.com"; UUID id=UUID.randomUUID();
        new UserRepository(db.jdbc()).insertVerifiedOrdinaryUser(id,email,new PasswordHasher().encode("Long-test-password-1"),"测试用户",db.clock().instant());
        var sessions=new SessionService(db.jdbc(),new byte[32],db.clock());
        var issued=sessions.login(email,"Long-test-password-1","test-source");
        db.jdbc().update("update user_account set status='DISABLED',credential_version=credential_version+1 where user_id=?",id.toString());
        assertThrows(BusinessException.class,()->sessions.resolve(issued.rawToken()));
        db.jdbc().update("update user_account set status='ENABLED' where user_id=?",id.toString());
        assertThrows(BusinessException.class,()->sessions.resolve(issued.rawToken()));
        var fresh=sessions.login(email,"Long-test-password-1","test-source");
        db.clock().advance(java.time.Duration.ofHours(8));
        assertThrows(BusinessException.class,()->sessions.resolve(fresh.rawToken()));
    }
    @Test void missingWrongAndDisabledLoginsHaveSameErrorAndLimitsBlockCorrectPassword() {
        var db=new AuthDatabaseFixture(); String email=UUID.randomUUID()+"@example.com";
        new UserRepository(db.jdbc()).insertVerifiedOrdinaryUser(UUID.randomUUID(),email,new PasswordHasher().encode("Long-test-password-1"),"测试用户",db.clock().instant());
        var sessions=new SessionService(db.jdbc(),new byte[32],db.clock());
        assertEquals("LOGIN_FAILED",assertThrows(BusinessException.class,()->sessions.login(UUID.randomUUID()+"@example.com","Long-test-password-1","other-source")).code());
        for(int i=0;i<10;i++) assertEquals("LOGIN_FAILED",assertThrows(BusinessException.class,()->sessions.login(email,"Wrong-test-password-1","test-source")).code());
        assertEquals("RATE_LIMITED",assertThrows(BusinessException.class,()->sessions.login(email,"Long-test-password-1","test-source")).code());
        db.clock().advance(java.time.Duration.ofMinutes(15));
        assertNotNull(sessions.login(email,"Long-test-password-1","test-source").identity());
    }
}
