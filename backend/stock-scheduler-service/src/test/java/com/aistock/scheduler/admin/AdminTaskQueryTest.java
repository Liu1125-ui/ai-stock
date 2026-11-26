package com.aistock.scheduler.admin;
import com.aistock.common.BusinessException;
import java.time.*;import org.junit.jupiter.api.*;import org.junit.jupiter.params.ParameterizedTest;import org.junit.jupiter.params.provider.ValueSource;import org.springframework.util.*;
import static org.junit.jupiter.api.Assertions.*;
class AdminTaskQueryTest {
 static LinkedMultiValueMap<String,String> valid(){var q=new LinkedMultiValueMap<String,String>();q.add("createdFrom","2026-10-01");q.add("createdTo","2026-10-20");return q;}
 @Test void defaultsAndShanghaiDateBoundaries(){var q=AdminTaskQuery.parseList(valid());assertEquals(20,q.limit());assertNull(q.type());assertEquals(Instant.parse("2026-09-30T16:00:00Z"),AdminTaskQuery.start(q));assertEquals(Instant.parse("2026-10-20T16:00:00Z"),AdminTaskQuery.end(q));}
 @Test void duplicateEvenIdenticalValuesAreRejected(){var q=valid();q.add("limit","20");q.add("limit","20");invalid(()->AdminTaskQuery.parseList(q));}
 @ParameterizedTest @ValueSource(strings={"0","51","-1","abc","2147483648",""," 20"}) void rejectsBadLimit(String value){var q=valid();q.add("limit",value);invalid(()->AdminTaskQuery.parseList(q));}
 @ParameterizedTest @ValueSource(strings={"sort","role","unknown"}) void rejectsUnknownParameters(String key){var q=valid();q.add(key,"x");invalid(()->AdminTaskQuery.parseList(q));}
 @Test void checksEnumsAndStockFormat(){for(var key:new String[]{"type","state","instrumentId"}){var q=valid();q.add(key,"unsafe");invalid(()->AdminTaskQuery.parseList(q));}var q=valid();q.add("type","COMPARISON_UPDATE");q.add("state","RECONCILING");q.add("instrumentId","SH600030");assertEquals("SH600030",AdminTaskQuery.parseList(q).instrumentId());}
 @ParameterizedTest @ValueSource(strings={"2026-02-30","2026-9-01","0000-01-01","0999-12-31","","2026-10-21"}) void badOrReversedDate(String date){var q=valid();q.set("createdFrom",date);invalid(()->AdminTaskQuery.parseList(q));}
 @Test void validUpperBoundaryUsesConvertedUtcRange(){var q=valid();q.set("createdFrom","9999-12-31");q.set("createdTo","9999-12-31");assertEquals(Instant.parse("9999-12-31T16:00:00Z"),AdminTaskQuery.end(AdminTaskQuery.parseList(q)));}
 @Test void requiresBothDates(){var q=valid();q.remove("createdTo");invalid(()->AdminTaskQuery.parseList(q));}
 @Test void validatesIdsAndAttemptParameters(){assertEquals("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",AdminTaskQuery.validateId("AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA"));invalid(()->AdminTaskQuery.validateId("1-1-1-1-1"));var q=new LinkedMultiValueMap<String,String>();q.add("type","MARKET_REFRESH");invalid(()->AdminTaskQuery.parseAttempts(q));}
 static void invalid(org.junit.jupiter.api.function.Executable action){assertEquals("TASK_QUERY_INVALID",assertThrows(BusinessException.class,action).code());}
}
