package com.aistock.user.account;

import com.aistock.common.BusinessException;
import com.aistock.user.session.SessionService;
import com.aistock.user.support.AuthDatabaseFixture;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("mysql") class ProfileServiceTest {
    @Test void revisionProtectsNicknameAndPasswordChangeRevokesEverySession(){
        var db=new AuthDatabaseFixture();var id=UUID.randomUUID();
        new UserRepository(db.jdbc()).insertVerifiedOrdinaryUser(id,"profile@example.com",new PasswordHasher().encode("Long-test-password-1"),"原昵称",db.clock().instant());
        var profiles=new ProfileService(db.jdbc(),db.clock());
        var sessions=new SessionService(db.jdbc(),new byte[32],db.clock());
        var first=sessions.login("profile@example.com","Long-test-password-1","one");
        var second=sessions.login("profile@example.com","Long-test-password-1","two");
        assertEquals(1,profiles.rename(id,"新昵称",0).revision());
        assertEquals("REVISION_CONFLICT",assertThrows(BusinessException.class,()->profiles.rename(id,"旧页面",0)).code());
        assertThrows(BusinessException.class,()->profiles.changePassword(id,"wrong-password-1","New-test-password-2"));
        assertNotNull(sessions.resolve(first.rawToken()));
        profiles.changePassword(id,"Long-test-password-1","New-test-password-2");
        assertThrows(BusinessException.class,()->sessions.resolve(first.rawToken()));
        assertThrows(BusinessException.class,()->sessions.resolve(second.rawToken()));
        assertNotNull(sessions.login("profile@example.com","New-test-password-2","three"));
        assertFalse(profiles.me(id).toString().contains("password"));
    }
    @Test void nicknameAndPasswordConstraintsRemainEnforced(){
        var db=new AuthDatabaseFixture();var id=UUID.randomUUID();
        new UserRepository(db.jdbc()).insertVerifiedOrdinaryUser(id,"valid@example.com",new PasswordHasher().encode("Long-test-password-1"),"测试用户",db.clock().instant());
        var profiles=new ProfileService(db.jdbc(),db.clock());
        assertThrows(BusinessException.class,()->profiles.rename(id,"一",0));
        assertThrows(BusinessException.class,()->profiles.rename(id,"含\n换行",0));
        assertThrows(BusinessException.class,()->profiles.changePassword(id,"Long-test-password-1","汉".repeat(25)));
        assertEquals(0,profiles.me(id).revision());
    }
    @Test void avatarIsBoundToProfileRevisionAndOlderRenameKeepsIt(){
        var db=new AuthDatabaseFixture();
        var id=UUID.randomUUID();
        new UserRepository(db.jdbc()).insertVerifiedOrdinaryUser(id,"avatar@example.com",
            new PasswordHasher().encode("Long-test-password-1"),"测试用户",db.clock().instant());
        var profiles=new ProfileService(db.jdbc(),db.clock());
        assertNull(profiles.me(id).avatarStyle());
        assertEquals("ocean",profiles.update(id,"测试用户",0,"ocean").avatarStyle());
        assertEquals("ocean",profiles.rename(id,"新昵称",1).avatarStyle());
        assertThrows(BusinessException.class,()->profiles.update(id,"测试用户",1,"plum"));
        assertThrows(BusinessException.class,()->profiles.update(id,"测试用户",2,"https://example.test/image"));
        assertEquals("ocean",profiles.me(id).avatarStyle());
        assertNull(profiles.update(id,"新昵称",2,"default").avatarStyle());
    }
}
