package com.aistock.notification.email;
import java.util.UUID;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MailPayloadCodecTest {
    @Test void payloadAuthenticationBindsTheRequestAndRejectsTampering() {
        var codec = new MailPayloadCodec(new byte[32], new byte[32]);
        var command = new EmailCommand(UUID.randomUUID(), UUID.randomUUID(), "member@example.com", "123456", Instant.parse("2026-09-30T00:10:00Z"));
        var encrypted = codec.encrypt(command);
        assertEquals(command, codec.decrypt(command.requestId(), encrypted));
        assertThrows(IllegalStateException.class, () -> codec.decrypt(UUID.randomUUID(), encrypted));
        encrypted.ciphertext()[0] ^= 1;
        assertThrows(IllegalStateException.class, () -> codec.decrypt(command.requestId(), encrypted));
    }
}
