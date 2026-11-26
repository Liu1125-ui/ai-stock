package com.aistock.prediction.question;
import com.aistock.common.BusinessException;import org.springframework.http.HttpStatus;import java.text.Normalizer;import java.util.UUID;
public record QuestionInput(UUID operationId,UUID analysisId,String question) {
 public static String normalize(String value){if(value==null)throw invalid();for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isISOControl(c))throw invalid();if(Character.isHighSurrogate(c)){if(i+1>=value.length()||!Character.isLowSurrogate(value.charAt(++i)))throw invalid();}else if(Character.isLowSurrogate(c))throw invalid();}String n=Normalizer.normalize(value,Normalizer.Form.NFC);int a=0,b=n.length();while(a<b&&space(n.charAt(a)))a++;while(b>a&&space(n.charAt(b-1)))b--;n=n.substring(a,b);if(n.codePointCount(0,n.length())<1||n.codePointCount(0,n.length())>300)throw invalid();return n;}
 private static boolean space(char c){return c==' '||c=='\u00a0'||c=='\u1680'||c>='\u2000'&&c<='\u200a'||c=='\u2028'||c=='\u2029'||c=='\u202f'||c=='\u205f'||c=='\u3000';}
 public QuestionInput normalized(){if(operationId==null||analysisId==null)throw invalid();return new QuestionInput(operationId,analysisId,normalize(question));}
 public static BusinessException invalid(){return new BusinessException("AI_REQUEST_INVALID","问题或请求编号不正确，问题限三百字且不能含控制字符",HttpStatus.BAD_REQUEST);}
}
