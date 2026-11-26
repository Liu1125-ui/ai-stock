package com.aistock.user.account;
import com.aistock.common.BusinessException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {
    @Test void passwordHasRandomSaltAndVerifiesWithoutPlaintextStorage() {
        var passwords = new PasswordHasher();
        String raw = "Long-test-password-1";
        String a = passwords.encode(raw);
        String b = passwords.encode(raw);
        assertNotEquals(a, b);
        assertTrue(a.startsWith("$2a$12$"));
        assertTrue(passwords.matches(raw, a));
        assertFalse(passwords.matches("wrong-password", a));
    }
    @Test void rejectsShortAndOver72BytePasswordsWithoutTrimming() {
        var passwords = new PasswordHasher();
        assertThrows(BusinessException.class, () -> passwords.encode("short"));
        assertThrows(BusinessException.class, () -> passwords.encode("中".repeat(25)));
        String padded = "  Long-password-1  ";
        String hash = passwords.encode(padded);
        assertTrue(passwords.matches(padded, hash));
        assertFalse(passwords.matches(padded.strip(), hash));
    }
}
