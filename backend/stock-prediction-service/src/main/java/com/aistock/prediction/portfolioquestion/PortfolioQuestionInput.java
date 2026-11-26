package com.aistock.prediction.portfolioquestion;
import com.fasterxml.jackson.core.*;import com.fasterxml.jackson.databind.*;import com.aistock.common.BusinessException;import com.aistock.prediction.question.QuestionInput;import org.springframework.http.HttpStatus;import java.util.*;import java.util.regex.Pattern;
import static com.aistock.prediction.portfolioquestion.PortfolioQuestionRecords.*;
public final class PortfolioQuestionInput {
 private static final ObjectMapper STRICT=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
 private static final Pattern CREDENTIAL=Pattern.compile("(?i)\\b(?:api[_ -]?key|authorization|smtp[_ -]?(?:password|token)|password)\\s*[:=]|\\bBearer\\s+\\S+|\\bsk-[A-Za-z0-9_-]{16,}");
 private PortfolioQuestionInput(){}
 public static String normalize(String value){String n=QuestionInput.normalize(value);if(CREDENTIAL.matcher(n).find())throw invalid();return n;}
 public static Request parse(byte[] body){if(body==null||body.length>4096)throw invalid();try{JsonNode n=STRICT.readTree(body);if(n==null||!n.isObject()||n.size()!=4)throw invalid();for(String k:List.of("operationId","analysisId","question"))if(!n.path(k).isTextual())throw invalid();if(!n.path("disclosureAccepted").isBoolean())throw invalid();var op=uuid(n.path("operationId").textValue());var id=uuid(n.path("analysisId").textValue());return new Request(op,id,normalize(n.path("question").textValue()),n.path("disclosureAccepted").booleanValue());}catch(BusinessException e){throw e;}catch(Exception e){throw invalid();}}
 static UUID uuid(String value){if(value==null||!value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))throw invalid();return UUID.fromString(value);}
 static BusinessException invalid(){return new BusinessException("AI_REQUEST_INVALID","问题或请求不正确，请勿填写密码或密钥",HttpStatus.BAD_REQUEST);}
 static BusinessException evidence(){return new BusinessException("PORTFOLIO_QUESTION_EVIDENCE_INVALID","这份估值依据不完整，暂不能解释，原结果保留",HttpStatus.UNPROCESSABLE_ENTITY);}
 static BusinessException unavailable(){return new BusinessException("PORTFOLIO_QUESTION_NO_VALUES","这份估值没有可分析金额，请先查看规则说明",HttpStatus.UNPROCESSABLE_ENTITY);}
 static BusinessException budget(){return new BusinessException("AI_EVIDENCE_TOO_LARGE","这份估值依据超出当前问答容量，原结果保留",HttpStatus.UNPROCESSABLE_ENTITY);}
}
