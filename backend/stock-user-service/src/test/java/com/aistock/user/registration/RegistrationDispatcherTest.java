package com.aistock.user.registration;
import com.aistock.common.ApiResponse;
import com.aistock.user.support.AuthDatabaseFixture;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("mysql")
class RegistrationDispatcherTest {
    @Test void responseTimeoutRetainsStableRequestAndRestartClearsPayloadAfterAcceptance() {
        var db=new AuthDatabaseFixture(); byte[] key=new byte[32];
        var service=new RegistrationService(db.jdbc(),key,key,db.clock());
        var challenge=service.request(UUID.randomUUID()+"@example.com","test-"+UUID.randomUUID());
        var requests=new java.util.ArrayList<UUID>();
        MailDeliveryClient client=new MailDeliveryClient() {
            public ApiResponse<DeliveryView> accept(Command command,String token) {
                requests.add(command.requestId());
                if(requests.size()==1) throw new IllegalStateException("Simulated acceptance response timeout");
                return ApiResponse.success(new DeliveryView(command.requestId(),"PENDING",command.expiresAt()),"test");
            }
            public ApiResponse<DeliveryView> find(UUID id,String token) { throw new UnsupportedOperationException(); }
        };
        new RegistrationDispatcher(db.jdbc(),new RegistrationPayloadCodec(key),client,"test").runOnce(db.clock().instant());
        assertEquals("PENDING",service.status(challenge.challengeId()).state());
        db.clock().advance(java.time.Duration.ofSeconds(5));
        new RegistrationDispatcher(db.jdbc(),new RegistrationPayloadCodec(key),client,"test").runOnce(db.clock().instant());
        assertEquals(requests.get(0),requests.get(1));
        assertEquals("ACCEPTED",service.status(challenge.challengeId()).state());
        assertNull(db.jdbc().queryForObject("select payload_ciphertext from registration_dispatch where challenge_id=?",byte[].class,challenge.challengeId().toString()));
    }
    @Test void existingEmailCreatesNoReusableRegistrationChallenge() {
        var db=new AuthDatabaseFixture(); byte[] key=new byte[32];
        String email=UUID.randomUUID()+"@example.com";
        new com.aistock.user.account.UserRepository(db.jdbc()).insertVerifiedOrdinaryUser(UUID.randomUUID(),email,"$2a$12$"+"a".repeat(53),"测试用户",db.clock().instant());
        var service=new RegistrationService(db.jdbc(),key,key,db.clock());
        var challenge=service.request(email,"test-"+UUID.randomUUID());
        assertEquals("PENDING",challenge.state());
        assertNotNull(new ChallengeRepository(db.jdbc()).findById(challenge.challengeId()).orElseThrow().invalidatedAt());
        assertEquals("PENDING",service.status(challenge.challengeId()).state());
        assertEquals(1,db.jdbc().queryForObject("select count(*) from registration_dispatch where challenge_id=? and payload_ciphertext is not null",Integer.class,challenge.challengeId().toString()));
    }
}
