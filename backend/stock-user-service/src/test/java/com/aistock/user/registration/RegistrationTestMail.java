package com.aistock.user.registration;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
/** Test-only access to the encrypted outbox; never added to production services. */
final class RegistrationTestMail {
    static MailDeliveryClient.Command read(JdbcTemplate jdbc,UUID challengeId,RegistrationPayloadCodec codec) {
        return jdbc.queryForObject("select request_id,payload_ciphertext,payload_nonce,payload_key_id from registration_dispatch where challenge_id=?",
                (rs,n)->codec.decrypt(UUID.fromString(rs.getString("request_id")),new RegistrationPayloadCodec.Payload(rs.getBytes("payload_ciphertext"),rs.getBytes("payload_nonce"),rs.getString("payload_key_id"))),challengeId.toString());
    }
}
