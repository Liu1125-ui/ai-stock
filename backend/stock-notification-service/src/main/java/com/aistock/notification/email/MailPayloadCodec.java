package com.aistock.notification.email;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class MailPayloadCodec {
    public record Encrypted(byte[] ciphertext, byte[] nonce, String keyId) { }
    private final byte[] encryptionKey;
    private final byte[] digestKey;
    private final SecureRandom random = new SecureRandom();
    public MailPayloadCodec(byte[] encryptionKey, byte[] digestKey) {
        if (encryptionKey.length != 32 || digestKey.length < 32) throw new IllegalArgumentException("Mail keys require 32 bytes");
        this.encryptionKey = encryptionKey.clone(); this.digestKey = digestKey.clone();
    }
    public String digest(EmailCommand command) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(digestKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(plain(command)));
        } catch (GeneralSecurityException error) { throw new IllegalStateException("Mail digest unavailable"); }
    }
    public Encrypted encrypt(EmailCommand command) {
        byte[] nonce = new byte[12]; random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(command.requestId().toString().getBytes(StandardCharsets.US_ASCII));
            return new Encrypted(cipher.doFinal(plain(command)), nonce, "v1");
        } catch (GeneralSecurityException error) { throw new IllegalStateException("Mail encryption unavailable"); }
    }
    public EmailCommand decrypt(UUID requestId, Encrypted encrypted) {
        if (!"v1".equals(encrypted.keyId())) throw new IllegalStateException("Unknown mail key version");
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, encrypted.nonce()));
            cipher.updateAAD(requestId.toString().getBytes(StandardCharsets.US_ASCII));
            String[] fields = new String(cipher.doFinal(encrypted.ciphertext()), StandardCharsets.UTF_8).split("\n", -1);
            if (fields.length != 5 || !requestId.toString().equals(fields[0])) throw new IllegalStateException("Invalid mail payload");
            return new EmailCommand(requestId, UUID.fromString(fields[1]), fields[2], fields[3], Instant.parse(fields[4]));
        } catch (GeneralSecurityException error) { throw new IllegalStateException("Mail payload cannot be decrypted"); }
    }
    private byte[] plain(EmailCommand command) {
        return String.join("\n", command.requestId().toString(), command.challengeId().toString(), command.email(), command.code(), command.expiresAt().toString()).getBytes(StandardCharsets.UTF_8);
    }
}
