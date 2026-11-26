package com.aistock.scheduler.admin;
import com.aistock.common.BusinessException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static com.aistock.scheduler.admin.AdminTaskActionRecords.*;

class AdminTaskActionInputTest {
 static final String ID="00000000-0000-0000-0000-000000000001";
 private byte[] json(String revision,String reason) {
  return ("{\"operationId\":\""+ID+"\",\"expectedRevision\":"+revision+",\"reasonCode\":\""+reason+"\"}").getBytes(StandardCharsets.UTF_8);
 }
 @Test void parsesExactFixedRequestAndLongRevisionWithoutPrecisionLoss() {
  var c=AdminTaskActionInput.parse(json("\"9007199254740993\"","RETRY_TRANSIENT_FAILURE"),Action.RETRY);
  assertEquals(ID,c.operationId().toString());assertEquals("9007199254740993",c.expectedRevision());
  assertEquals(9007199254740993L,AdminTaskActionInput.revision(c.expectedRevision()));
 }
 @ParameterizedTest @ValueSource(strings={"-1","01","+1","1.0","","9223372036854775808"," 1","1 "})
 void rejectsInvalidRevisionStrings(String value) {
  assertThrows(BusinessException.class,()->AdminTaskActionInput.parse(json("\""+value+"\"","RETRY_TRANSIENT_FAILURE"),Action.RETRY));
 }
 @Test void acceptsZeroAndLongMaximumButRejectsNumericJson() {
  assertEquals(0,AdminTaskActionInput.revision("0"));
  assertEquals(Long.MAX_VALUE,AdminTaskActionInput.revision("9223372036854775807"));
  assertThrows(BusinessException.class,()->AdminTaskActionInput.parse(json("1","RETRY_TRANSIENT_FAILURE"),Action.RETRY));
 }
 @ParameterizedTest @ValueSource(strings={"{}","[]","null","{\"operationId\":null}","{\"operationId\":1}","{} {}"})
 void rejectsMalformedOrMissingFields(String value) {
  assertThrows(BusinessException.class,()->AdminTaskActionInput.parse(value.getBytes(StandardCharsets.UTF_8),Action.RETRY));
 }
 @Test void rejectsDuplicateUnknownAndWrongActionReason() {
  var valid=new String(json("\"0\"","RETRY_TRANSIENT_FAILURE"),StandardCharsets.UTF_8);
  for(String suffix:new String[]{",\"operationId\":\""+ID+"\"",",\"note\":null",",\"actorUserId\":\"fake\""}) {
   var bad=valid.substring(0,valid.length()-1)+suffix+"}";
   assertThrows(BusinessException.class,()->AdminTaskActionInput.parse(bad.getBytes(StandardCharsets.UTF_8),Action.RETRY));
  }
  assertThrows(BusinessException.class,()->AdminTaskActionInput.parse(json("\"0\"","CANCEL_NOT_NEEDED"),Action.RETRY));
  assertThrows(BusinessException.class,()->AdminTaskActionInput.parse(json("\"0\"","RETRY_TRANSIENT_FAILURE"),Action.CANCEL));
  assertDoesNotThrow(()->AdminTaskActionInput.parse(json("\"0\"","CANCEL_DUPLICATE_REQUEST"),Action.CANCEL));
 }
 @Test void boundsBytesAndRejectsInvalidUtf8() {
  assertThrows(BusinessException.class,()->AdminTaskActionInput.parse(new byte[4097],Action.RETRY));
  assertThrows(BusinessException.class,()->AdminTaskActionInput.parse(null,Action.RETRY));
  assertThrows(BusinessException.class,()->AdminTaskActionInput.parse(new byte[]{(byte)0xff},Action.RETRY));
  var base=json("\"0\"","RETRY_TRANSIENT_FAILURE");var padded=new byte[4096];
  java.util.Arrays.fill(padded,(byte)' ');System.arraycopy(base,0,padded,0,base.length);
  assertDoesNotThrow(()->AdminTaskActionInput.parse(padded,Action.RETRY));
 }
 @ParameterizedTest @ValueSource(strings={"bad","1-1-1-1-1","00000000-0000-0000-0000-00000000000A","00000000-0000-0000-0000-000000000001 "})
 void rejectsNonCanonicalId(String id) {assertThrows(BusinessException.class,()->AdminTaskActionInput.validateId(id));}
}
