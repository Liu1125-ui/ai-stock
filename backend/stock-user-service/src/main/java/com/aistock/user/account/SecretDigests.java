package com.aistock.user.account;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class SecretDigests {
    private final byte[] key;
    public SecretDigests(byte[] key) {
        if (key == null || key.length < 32) throw new IllegalArgumentException("HMAC key requires at least 32 bytes");
        this.key = key.clone();
    }
    public String code(UUID id, String email, String code) { return keyed("REGISTER", id.toString(), EmailAddress.normalize(email), code); }
    public String scope(String scope, String subject) { return keyed("RATE", scope, subject); }
    public String session(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (GeneralSecurityException unavailable) { throw new IllegalStateException("SHA-256 unavailable", unavailable); }
    }
    public boolean matchesCode(UUID id, String email, String code, String expected) {
        if (expected == null || !expected.matches("[a-f0-9]{64}")) return false;
        return MessageDigest.isEqual(code(id, email, code).getBytes(StandardCharsets.US_ASCII), expected.getBytes(StandardCharsets.US_ASCII));
    }
    private String keyed(String... parts) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            for (String part : parts) {
                byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
                mac.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
                mac.update(bytes);
            }
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (GeneralSecurityException unavailable) { throw new IllegalStateException("HMAC unavailable", unavailable); }
    }
}
