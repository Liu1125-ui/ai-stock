package com.aistock.prediction.comparisonai;
import java.util.UUID;import com.aistock.common.BusinessException;import org.springframework.http.HttpStatus;
public record ComparisonAiInput(UUID operationId,UUID comparisonId) {
 public void validate(){if(operationId==null||comparisonId==null)throw new BusinessException("AI_REQUEST_INVALID","解释请求编号不正确",HttpStatus.UNPROCESSABLE_ENTITY);}
}
