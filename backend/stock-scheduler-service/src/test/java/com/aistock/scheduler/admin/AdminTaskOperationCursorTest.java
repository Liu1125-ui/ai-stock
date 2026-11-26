package com.aistock.scheduler.admin;
import com.aistock.common.BusinessException;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.aistock.scheduler.admin.AdminTaskActionRecords.*;
class AdminTaskOperationCursorTest {
 final String task=UUID.randomUUID().toString();
 final AdminTaskOperationCursorCodec codec=new AdminTaskOperationCursorCodec();
 @Test void roundTripsBindsTaskAndLimitAndRejectsOversizeOrTampering() {
  var a=new HistoryKey(Instant.parse("2026-10-20T12:00:00Z"),"00000000-0000-0000-0000-000000000002");
  var b=new HistoryKey(a.requestedAt(),"00000000-0000-0000-0000-000000000001");
  var c=new HistoryCursor(1,task,20,a,b);var token=codec.encode(c);
  assertEquals(c,codec.decode(token,task,20));
  assertThrows(BusinessException.class,()->codec.decode(token,UUID.randomUUID().toString(),20));
  assertThrows(BusinessException.class,()->codec.decode(token,task,21));
  for(String value:List.of("bad!","a".repeat(4097),Base64.getUrlEncoder().withoutPadding().encodeToString("{} {}".getBytes())))assertThrows(BusinessException.class,()->codec.decode(value,task,20));
  assertThrows(BusinessException.class,()->codec.decode(codec.encode(new HistoryCursor(2,task,20,a,b)),task,20));
  assertThrows(BusinessException.class,()->codec.decode(codec.encode(new HistoryCursor(1,task,20,b,a)),task,20));
 }
}
