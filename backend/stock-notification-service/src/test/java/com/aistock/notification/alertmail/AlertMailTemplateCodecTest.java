package com.aistock.notification.alertmail;
import com.aistock.notification.alert.*;import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;import java.util.*;import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
class AlertMailTemplateCodecTest {
 @Test void snapshotTemplateUsesFixedEvidenceAndFourConditions()throws Exception{
  var e=new ObjectMapper().findAndRegisterModules().readValue(getClass().getResourceAsStream("/alert/evidence.json"),AlertEvidenceClient.Evidence.class);
  for(String condition:List.of("PRICE_ABOVE","PRICE_BELOW","DAILY_RISE","DAILY_FALL")){
   var s=new AlertEvaluationRecords.Snapshot(UUID.randomUUID(),UUID.randomUUID(),e.instrumentId(),0,condition,"2.5",e.tradeDate(),e,e.capturedAt());
   String text=AlertMailTemplate.body(s);assertTrue(text.contains("SH600030"));assertTrue(text.contains(e.tradeDate().toString()));assertTrue(text.contains("2.5"));assertTrue(text.contains(e.rawClose()));assertTrue(text.contains(e.capturedAt().toString()));assertFalse(text.contains("验证码"));
  }
 }
 @Test void encryptionBindsDeliveryAndCannotBeReinterpretedAsVerification(){
  var codec=new AlertMailCodec(new byte[32],new byte[32]);var id=UUID.randomUUID();var p=codec.encrypt(id,"fixture@example.com","固定快照\n内容");
  assertEquals("固定快照\n内容",codec.decrypt(id,p).body());assertEquals("fixture@example.com",codec.decrypt(id,p).recipient());
  assertFalse(codec.decrypt(id,p).toString().contains("fixture@example.com"));
  assertThrows(RuntimeException.class,()->codec.decrypt(UUID.randomUUID(),p));p.ciphertext()[0]^=1;assertThrows(RuntimeException.class,()->codec.decrypt(id,p));
  assertThrows(RuntimeException.class,()->codec.encrypt(id,"fixture@example.com\r\nBcc:bad@example.com","body"));
 }
 @Test void malformedSnapshotsAndUnknownKeysFailClosed()throws Exception{
  var codec=new AlertMailCodec(new byte[32],new byte[32]);var id=UUID.randomUUID();var p=codec.encrypt(id,"fixture@example.com","body");assertThrows(RuntimeException.class,()->codec.decrypt(id,new AlertMailCodec.Encrypted(p.ciphertext(),p.nonce(),"verification-v1")));
  assertThrows(RuntimeException.class,()->AlertMailTemplate.body(null));
 }
}
