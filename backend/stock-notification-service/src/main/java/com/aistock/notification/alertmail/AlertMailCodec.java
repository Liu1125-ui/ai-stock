package com.aistock.notification.alertmail;
import java.nio.charset.StandardCharsets;import java.security.*;import java.util.*;
import javax.crypto.*;import javax.crypto.spec.*;import com.fasterxml.jackson.databind.ObjectMapper;
public final class AlertMailCodec {
 public record Encrypted(byte[] ciphertext,byte[] nonce,String keyId){}
 public record Payload(String recipient,String body){@Override public String toString(){return "AlertMailPayload[redacted]";}}
 private final byte[] key,digestKey;private final SecureRandom random=new SecureRandom();private final ObjectMapper mapper=new ObjectMapper();
 public AlertMailCodec(byte[] key,byte[] digestKey){if(key==null||key.length!=32||digestKey==null||digestKey.length<32)throw new IllegalArgumentException("Alert mail keys require 32 bytes");this.key=key.clone();this.digestKey=digestKey.clone();}
 public String digest(String recipient){if(!com.aistock.notification.alert.AlertMailAccountClient.validAddress(recipient))throw invalid();try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(digestKey,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(("alert-mail-recipient-v1\0"+recipient.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8)));}catch(GeneralSecurityException e){throw invalid();}}
 public Encrypted encrypt(UUID id,String recipient,String body){new AlertMailTransport.Command(recipient,"股票预警",body);byte[] nonce=new byte[12];random.nextBytes(nonce);try{var c=cipher(Cipher.ENCRYPT_MODE,id,nonce,"alert-v1");return new Encrypted(c.doFinal(mapper.writeValueAsBytes(new Payload(recipient,body))),nonce,"alert-v1");}catch(Exception e){throw invalid();}}
 public Payload decrypt(UUID id,Encrypted encrypted){if(encrypted==null||!"alert-v1".equals(encrypted.keyId())||encrypted.nonce()==null||encrypted.nonce().length!=12||encrypted.ciphertext()==null||encrypted.ciphertext().length>65536)throw invalid();try{var c=cipher(Cipher.DECRYPT_MODE,id,encrypted.nonce(),encrypted.keyId());var p=mapper.readValue(c.doFinal(encrypted.ciphertext()),Payload.class);new AlertMailTransport.Command(p.recipient(),"股票预警",p.body());return p;}catch(Exception e){throw invalid();}}
 private Cipher cipher(int mode,UUID id,byte[] nonce,String version)throws GeneralSecurityException{var c=Cipher.getInstance("AES/GCM/NoPadding");c.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));c.updateAAD(("alert-mail|"+version+"|"+Objects.requireNonNull(id)).getBytes(StandardCharsets.US_ASCII));return c;}
 private static AlertMailPayloadException invalid(){return new AlertMailPayloadException();}
}
