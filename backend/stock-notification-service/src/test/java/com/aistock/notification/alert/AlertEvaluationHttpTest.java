package com.aistock.notification.alert;
import com.aistock.common.*;import com.fasterxml.jackson.databind.ObjectMapper;import java.util.*;import org.junit.jupiter.api.Test;import org.springframework.test.web.servlet.setup.MockMvcBuilders;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;import static org.mockito.Mockito.*;
class AlertEvaluationHttpTest {
 @Test void tokenIsRequiredBeforeBodyParsingAndPayloadIsBounded()throws Exception{var service=mock(AlertEvaluationService.class);var mvc=MockMvcBuilders.standaloneSetup(new AlertEvaluationController(service),new AlertRuleScanController(service)).setControllerAdvice(new ApiExceptionHandler()).addFilters(new AlertEvaluationTokenFilter("fixture-token",new ObjectMapper())).build();
  mvc.perform(post("/internal/v1/alert-evaluations").contentType("application/json").content("broken")).andExpect(status().isUnauthorized());
  mvc.perform(post("/internal/v1/alert-evaluations").header("X-Service-Token","fixture-token").contentType("application/json").content("x".repeat(4097))).andExpect(status().isPayloadTooLarge());
  mvc.perform(post("/internal/v1/alert-evaluations").header("X-Service-Token","fixture-token").contentType("application/json").content("{}")).andExpect(status().isBadRequest());
  mvc.perform(get("/internal/v1/alerts/due")).andExpect(status().isUnauthorized());
  mvc.perform(get("/internal/v1/alert-evaluations;probe=x/operations/"+UUID.randomUUID())).andExpect(status().isUnauthorized());verifyNoInteractions(service);
 }
 @Test void validatedRequestUsesFixedIdsAndNoCallerOwner()throws Exception{var service=mock(AlertEvaluationService.class);var mvc=MockMvcBuilders.standaloneSetup(new AlertEvaluationController(service)).setControllerAdvice(new ApiExceptionHandler()).addFilters(new AlertEvaluationTokenFilter("fixture-token",new ObjectMapper())).build();var op=UUID.randomUUID();String body="{\"operationId\":\""+op+"\",\"ruleId\":\""+UUID.randomUUID()+"\",\"revision\":0,\"evidenceId\":\""+UUID.randomUUID()+"\",\"leaseUntil\":\"2026-10-10T09:00:00Z\"}";when(service.evaluate(any())).thenReturn(new AlertEvaluationRecords.EvaluationReceipt(op,"RUNNING",null,null));
  mvc.perform(post("/internal/v1/alert-evaluations").header("X-Service-Token","fixture-token").contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.data.operationId").value(op.toString()));
  mvc.perform(post("/internal/v1/alert-evaluations").header("X-Service-Token","fixture-token").contentType("application/json").content(body.replace("\"revision\":0","\"revision\":\"0\""))).andExpect(status().isBadRequest());
  mvc.perform(post("/internal/v1/alert-evaluations").header("X-Service-Token","fixture-token").contentType("application/json").content(body.replace("\"revision\":0","\"ownerId\":\"forged\",\"revision\":0"))).andExpect(status().isBadRequest());verify(service,times(1)).evaluate(any());
 }
}
