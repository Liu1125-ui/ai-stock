package com.aistock.user.registration;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class RegistrationPayloadCodec {
    public record Payload(byte[] ciphertext, byte[] nonce, String keyId) { }
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();
    public RegistrationPayloadCodec(byte[] key) {
        if (key.length != 32) throw new IllegalArgumentException("Registration payload key requires 32 bytes");
        this.key = key.clone();
    }
    public Payload encrypt(MailDeliveryClient.Command command) {
        byte[] nonce = new byte[12]; random.nextBytes(nonce);
        try {
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key,"AES"), new GCMParameterSpec(128,nonce));
            cipher.updateAAD(command.requestId().toString().getBytes(StandardCharsets.US_ASCII));
            String plain = String.join("\n",command.requestId().toString(),command.challengeId().toString(),command.email(),command.code(),command.expiresAt().toString());
            return new Payload(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)),nonce,"v1");
        } catch(GeneralSecurityException error) { throw new IllegalStateException("Registration encryption unavailable"); }
    }
    public MailDeliveryClient.Command decrypt(UUID requestId, Payload payload) {
        if(!"v1".equals(payload.keyId())) throw new IllegalStateException("Unknown registration key version");
        try {
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,payload.nonce()));
            cipher.updateAAD(requestId.toString().getBytes(StandardCharsets.US_ASCII));
            var fields = new String(cipher.doFinal(payload.ciphertext()),StandardCharsets.UTF_8).split("\n",-1);
            if(fields.length!=5 || !requestId.toString().equals(fields[0])) throw new IllegalStateException("Invalid registration payload");
            return new MailDeliveryClient.Command(requestId,UUID.fromString(fields[1]),fields[2],fields[3],Instant.parse(fields[4]));
        } catch(GeneralSecurityException error) { throw new IllegalStateException("Registration payload cannot be decrypted"); }
    }
}
