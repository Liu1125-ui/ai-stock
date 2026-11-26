package com.aistock.prediction.portfolioquestion;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import java.nio.charset.StandardCharsets;import java.util.*;
class PortfolioQuestionInputTest {
 byte[] body(String fields){return fields.getBytes(StandardCharsets.UTF_8);}
 String valid(){return "{\"operationId\":\""+UUID.randomUUID()+"\",\"analysisId\":\""+QuestionEvidence.ID+"\",\"question\":\"  怎么理解盈亏？  \",\"disclosureAccepted\":true}";}
 @Test void normalizesNfcAndUnicodeCodepointLength(){assertEquals("é",PortfolioQuestionInput.normalize(" e\u0301 "));assertEquals(300,PortfolioQuestionInput.normalize("😀".repeat(300)).codePointCount(0,600));assertThrows(Exception.class,()->PortfolioQuestionInput.normalize("😀".repeat(301)));}
 @Test void strictRequestRejectsSmugglingAndSensitiveCredentialPatterns(){assertEquals("怎么理解盈亏？",PortfolioQuestionInput.parse(body(valid())).question());for(var v:List.of(valid()+"{}",valid().replace("{","{\"ownerId\":\"x\","),valid().replace("{","{\"question\":\"other\","),valid().replace("true","\"true\""))){assertThrows(Exception.class,()->PortfolioQuestionInput.parse(body(v)));}assertThrows(Exception.class,()->PortfolioQuestionInput.normalize("API_KEY=sk-abcdefghijklmnopqrstuvwxy"));}
 @Test void controlsEmptyOversizedAndMissingBooleanAreRejected(){for(String v:List.of(" ","a\nb","a\u0000b")){assertThrows(Exception.class,()->PortfolioQuestionInput.normalize(v));}assertThrows(Exception.class,()->PortfolioQuestionInput.parse(new byte[4097]));assertThrows(Exception.class,()->PortfolioQuestionInput.parse(body(valid().replace(",\"disclosureAccepted\":true",""))));}
}
