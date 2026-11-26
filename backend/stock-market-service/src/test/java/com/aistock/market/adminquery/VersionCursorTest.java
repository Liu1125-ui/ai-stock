package com.aistock.market.adminquery;
import java.time.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class VersionCursorTest {
 private final VersionCursor codec=new VersionCursor();
 @Test void roundTripSameTimestampPreservesUuidOrderAndFilterBinding(){var c=new VersionCursor.Cursor("SH600030","baostock",Instant.parse("2026-10-22T04:00:00.123Z"),UUID.fromString("00000000-0000-0000-0000-000000000002"));var encoded=codec.encode(c);assertEquals(c,codec.decode(encoded,"SH600030","baostock"));assertThrows(com.aistock.common.BusinessException.class,()->codec.decode(encoded,"SH600276","baostock"));assertThrows(com.aistock.common.BusinessException.class,()->codec.decode(encoded,"SH600030","tushare"));}
 @Test void rejectsMalformedOversizedAndAmbiguousCursor(){for(var json:List.of("{}","{\"v\":1,\"v\":2}","{\"v\":1,\"other\":0}")){var raw=Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));assertThrows(com.aistock.common.BusinessException.class,()->codec.decode(raw,"SH600030",null));}for(var bad:List.of("?","a".repeat(513),"abcd="))assertThrows(com.aistock.common.BusinessException.class,()->codec.decode(bad,"SH600030",null));}
}
