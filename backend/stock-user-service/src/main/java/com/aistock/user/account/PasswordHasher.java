package com.aistock.user.account;

import com.aistock.common.BusinessException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Passwords are never normalized: spaces are part of the credential. */
public final class PasswordHasher {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public String encode(String raw) {
        if (!valid(raw)) {
            throw new BusinessException("VALIDATION_FAILED", "密码须为12至64个字符且不超过72字节", HttpStatus.BAD_REQUEST);
        }
        return encoder.encode(raw);
    }

    public boolean matches(String raw, String encoded) {
        return valid(raw) && encoded != null && encoder.matches(raw, encoded);
    }

    private boolean valid(String raw) {
        if (raw == null) return false;
        int length = raw.codePointCount(0, raw.length());
        return length >= 12 && length <= 64 && raw.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
