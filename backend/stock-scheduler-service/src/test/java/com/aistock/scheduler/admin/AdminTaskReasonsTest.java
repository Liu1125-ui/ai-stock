package com.aistock.scheduler.admin;
import java.util.*;import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;
class AdminTaskReasonsTest {
 @Test void knownCodesHaveSafeReadableExplanations(){for(var code:List.of("SOURCE_TIMEOUT","RESULT_UNKNOWN","COMPARISON_CYCLE_EXPIRED")){var r=AdminTaskReasons.safe(code);assertEquals(code,r.code());assertFalse(r.message().isBlank());}}
 @Test void unknownOrPrivateTextNeverEchoed(){for(var code:List.of("NEW_UNKNOWN_CODE","/private/secret","password=abc SQL error","X".repeat(1000))){var r=AdminTaskReasons.safe(code);assertNull(r.code());assertEquals("未识别原因，请联系管理员核对",r.message());}}
 @Test void nullIsAbsenceNotSuccess(){assertEquals("暂无原因记录",AdminTaskReasons.safe(null).message());}
}
