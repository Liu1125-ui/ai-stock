package com.aistock.user.account;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SecretDigestsTest {
    private final byte[] key = "0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    @Test void codeIsBoundToChallengeEmailAndSecretKey() {
        var digests = new SecretDigests(key);
        var id = UUID.randomUUID();
        String hash = digests.code(id, "a@example.com", "123456");
        assertTrue(hash.matches("[a-f0-9]{64}"));
        assertEquals(hash, digests.code(id, "a@example.com", "123456"));
        assertNotEquals(hash, digests.code(UUID.randomUUID(), "a@example.com", "123456"));
        assertNotEquals(hash, digests.code(id, "b@example.com", "123456"));
        assertNotEquals(hash, new SecretDigests(new byte[32]).code(id, "a@example.com", "123456"));
    }
    @Test void sessionOnlyStoresAnIrreversibleFingerprint() {
        var digests = new SecretDigests(key);
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", digests.session("abc"));
        assertThrows(IllegalArgumentException.class, () -> new SecretDigests(new byte[8]));
    }
}
