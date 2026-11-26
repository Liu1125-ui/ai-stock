package com.aistock.prediction.question;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import java.util.*;
class QuestionInputTest {
 @Test void unicodeNormalizationAndBoundaries(){assertEquals("é",QuestionInput.normalize("\u00a0e\u0301\u3000"));assertEquals("😀".repeat(300),QuestionInput.normalize("😀".repeat(300)));for(String s:new String[]{"", "\u00a0 ","😀".repeat(301),"问题\u0000","a\n",String.valueOf('\ud800')})assertThrows(com.aistock.common.BusinessException.class,()->QuestionInput.normalize(s));assertEquals("\ufeffx",QuestionInput.normalize("\ufeffx"));}
 @Test void normalizedInputPreservesIdentity(){var in=new QuestionInput(UUID.randomUUID(),UUID.randomUUID()," 风险？ ");assertEquals(in.operationId(),in.normalized().operationId());assertEquals("风险？",in.normalized().question());}
}
