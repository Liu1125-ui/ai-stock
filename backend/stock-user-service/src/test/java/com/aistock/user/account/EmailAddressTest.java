package com.aistock.user.account;

import com.aistock.common.BusinessException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmailAddressTest {
    @Test void normalizesLoginKeyWithoutMergingAliases() {
        assertEquals("user@example.com", EmailAddress.normalize(" User@Example.COM "));
        assertNotEquals(EmailAddress.normalize("u+a@example.com"), EmailAddress.normalize("u@example.com"));
    }
    @Test void rejectsMalformedAndUnsupportedAddresses() {
        for (String value : new String[]{"bad", "a@@example.com", "a\nb@example.com", "a b@example.com", "用户@example.com", ""}) {
            assertThrows(BusinessException.class, () -> EmailAddress.normalize(value), value);
        }
        assertThrows(BusinessException.class, () -> EmailAddress.normalize(null));
        assertThrows(BusinessException.class, () -> EmailAddress.normalize("a".repeat(65) + "@example.com"));
    }
}
