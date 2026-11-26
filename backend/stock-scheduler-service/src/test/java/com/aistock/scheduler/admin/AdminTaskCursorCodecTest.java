package com.aistock.scheduler.admin;
import com.aistock.common.BusinessException;import com.fasterxml.jackson.databind.*;
import java.time.*;import java.util.*;import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;import static com.aistock.scheduler.admin.AdminTaskRecords.*;
class AdminTaskCursorCodecTest {
 final AdminTaskCursorCodec codec=new AdminTaskCursorCodec();final TaskQuery query=AdminTaskQuery.parseList(AdminTaskQueryTest.valid());
 final String id="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
 SortKey key(String time){return new SortKey(Instant.parse(time),id);}
 @Test void roundTripPreservesMillisecondsAndScope(){var c=new ListCursor(1,query,key("2026-10-20T00:00:00.123Z"),key("2026-10-19T00:00:00.122Z"));assertEquals(c,codec.decodeList(codec.encodeList(c),query));var a=new AttemptCursor(1,id,20,110,20);assertEquals(a,codec.decodeAttempts(codec.encodeAttempts(a),id,20));}
 @Test void differentFiltersOrTaskCannotReuseCursor(){var c=new ListCursor(1,query,key("2026-10-20T00:00:00Z"),key("2026-10-19T00:00:00Z"));var q=new TaskQuery("MARKET_REFRESH",null,null,query.createdFrom(),query.createdTo(),20);bad(()->codec.decodeList(codec.encodeList(c),q));var a=new AttemptCursor(1,id,20,100,20);bad(()->codec.decodeAttempts(codec.encodeAttempts(a),"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",20));bad(()->codec.decodeAttempts(codec.encodeAttempts(a),id,50));}
 @Test void malformedAndUnboundedCursorRejected(){for(var s:List.of("", "not-json", "x".repeat(4097),raw("{\"version\":999}"),raw("{\"version\":1,\"extra\":true}")))bad(()->codec.decodeList(s,query));}
 @Test void rejectsInvalidOrderingPrecisionAndOrdinals(){bad(()->codec.decodeList(codec.encodeList(new ListCursor(1,query,key("2026-10-19T00:00:00Z"),key("2026-10-20T00:00:00Z"))),query));bad(()->codec.decodeList(codec.encodeList(new ListCursor(1,query,key("2026-10-20T00:00:00.123456Z"),key("2026-10-19T00:00:00Z"))),query));for(int after:new int[]{-1,111})bad(()->codec.decodeAttempts(codec.encodeAttempts(new AttemptCursor(1,id,20,110,after)),id,20));}
 static String raw(String s){return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 void bad(org.junit.jupiter.api.function.Executable e){assertEquals("TASK_QUERY_INVALID",assertThrows(BusinessException.class,e).code());}
}
