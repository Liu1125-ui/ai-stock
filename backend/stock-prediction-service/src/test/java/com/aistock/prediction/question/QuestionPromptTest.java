package com.aistock.prediction.question;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class QuestionPromptTest {
 @Test void examplesPassTheSameRendererAndNeverCopyFactValues()throws Exception{for(boolean prediction:List.of(false,true)){var facts=QuestionFacts.build(QuestionSamples.analysis(prediction),"为什么？","test",QuestionSamples.M);var prompt=QuestionPrompt.build(facts.values(),QuestionSamples.M);String marker="本次可用格式示例：";assertTrue(prompt.contains(marker));var example=QuestionSamples.M.readTree(prompt.substring(prompt.lastIndexOf(marker)+marker.length()));var answer=QuestionAnswerTemplate.render(example,facts.values(),QuestionSamples.M);assertEquals(prediction?"PREDICTION":"RISK",answer.topic());assertFalse(prompt.contains("25.8元"));for(String word:List.of("等于","上升","下降","即使是否定句","禁止换行","500"))assertTrue(prompt.contains(word));}assertEquals("grounded-stock-question-v2",QuestionFacts.PROMPT);}
}
