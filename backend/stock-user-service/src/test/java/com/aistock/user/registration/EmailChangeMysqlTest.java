package com.aistock.user.registration;

import com.aistock.common.BusinessException;
import com.aistock.user.account.PasswordHasher;
import com.aistock.user.account.UserRepository;
import com.aistock.user.session.SessionRepository;
import com.aistock.user.support.AuthDatabaseFixture;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("mysql")
class EmailChangeMysqlTest {
    @Test void changesOnlyAfterVerificationAndRevokesExistingSessions() {
        var db = new AuthDatabaseFixture();
        var jdbc = db.jdbc();
        var now = db.clock().instant();
        var key = new byte[32];
        var service = new EmailChangeService(jdbc,key,new RegistrationPayloadCodec(key),db.clock(),new RegistrationService(jdbc,key,key,db.clock()));
        var users = new UserRepository(jdbc);
        var sessions = new SessionRepository(jdbc);
        var owner = UUID.randomUUID();
        var other = UUID.randomUUID();
        String password = "fixture-password-17";
        String hash = new PasswordHasher().encode(password);
        users.insertVerifiedOrdinaryUser(owner,"old@example.com",hash,"owner",now);
        users.insertVerifiedOrdinaryUser(other,"other@example.com",hash,"other",now);
        String tokenHash = "0".repeat(64);
        sessions.create(UUID.randomUUID(),owner,tokenHash,users.findById(owner).orElseThrow().credentialVersion(),now,now.plus(Duration.ofHours(1)));
        assertThrows(BusinessException.class,()->service.request(owner,1,"new@example.com","wrong-password","fixture"));
        var issued = service.request(owner,1,"NEW@example.com",password,"fixture");
        assertEquals("old@example.com",users.findById(owner).orElseThrow().email());
        assertTrue(sessions.resolve(tokenHash,now).isPresent());
        assertTrue(new ChallengeRepository(jdbc).findById(issued.challengeId()).isEmpty());
        String code = jdbc.queryForObject("select request_id,payload_ciphertext,payload_nonce,payload_key_id from registration_dispatch where challenge_id=?",(rs,n)->new RegistrationPayloadCodec(key).decrypt(UUID.fromString(rs.getString(1)),new RegistrationPayloadCodec.Payload(rs.getBytes(2),rs.getBytes(3),rs.getString(4))).code(),issued.challengeId().toString());
        assertThrows(BusinessException.class,()->service.status(other,1,issued.challengeId(),"fixture"));
        assertThrows(BusinessException.class,()->service.confirm(other,1,issued.challengeId(),code,"fixture"));
        assertThrows(BusinessException.class,()->service.confirm(owner,1,issued.challengeId(),code.equals("000000")?"000001":"000000","fixture"));
        assertEquals(1,jdbc.queryForObject("select failed_attempts from registration_challenge where challenge_id=?",Integer.class,issued.challengeId().toString()));
        service.confirm(owner,1,issued.challengeId(),code,"fixture");
        assertEquals("new@example.com",users.findById(owner).orElseThrow().email());
        assertTrue(users.findByEmail("old@example.com").isEmpty());
        assertTrue(sessions.resolve(tokenHash,now).isEmpty());
        assertThrows(BusinessException.class,()->service.confirm(owner,2,issued.challengeId(),code,"fixture"));
    }
    @Test void rejectsSupersededExpiredAndNewlyOccupiedTargets() {
        var db=new AuthDatabaseFixture();
        var jdbc=db.jdbc();
        var key=new byte[32];
        var codec=new RegistrationPayloadCodec(key);
        var service=new EmailChangeService(jdbc,key,codec,db.clock(),new RegistrationService(jdbc,key,key,db.clock()));
        var users=new UserRepository(jdbc);
        var user=UUID.randomUUID();
        var password="fixture-password-27";
        var hash=new PasswordHasher().encode(password);
        users.insertVerifiedOrdinaryUser(user,"owner@example.com",hash,"owner",db.clock().instant());
        var first=service.request(user,1,"first@example.com",password,"fixture");
        var firstCode=RegistrationTestMail.read(jdbc,first.challengeId(),codec).code();
        assertThrows(BusinessException.class,()->service.request(user,1,"different@example.com",password,"fixture"));
        db.clock().advance(Duration.ofSeconds(61));
        var replacement=service.request(user,1,"replacement@example.com",password,"fixture");
        assertEquals("EXPIRED",service.status(user,1,first.challengeId(),"fixture").state());
        assertThrows(BusinessException.class,()->service.confirm(user,1,first.challengeId(),firstCode,"fixture"));
        var validCode=RegistrationTestMail.read(jdbc,replacement.challengeId(),codec).code();
        users.insertVerifiedOrdinaryUser(UUID.randomUUID(),"replacement@example.com",hash,"other",db.clock().instant());
        assertThrows(BusinessException.class,()->service.confirm(user,1,replacement.challengeId(),validCode,"fixture"));
        assertEquals("owner@example.com",users.findById(user).orElseThrow().email());
        assertNull(jdbc.queryForObject("select consumed_at from registration_challenge where challenge_id=?",java.sql.Timestamp.class,replacement.challengeId().toString()));
        db.clock().advance(Duration.ofMinutes(11));
        assertEquals("EXPIRED",service.status(user,1,replacement.challengeId(),"fixture").state());
        assertThrows(BusinessException.class,()->service.confirm(user,1,replacement.challengeId(),validCode,"fixture"));
    }

}
