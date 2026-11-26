package com.aistock.market.alert;
import com.aistock.market.ingestion.CandidateRequestFilter;import com.aistock.common.ApiExceptionHandler;
import org.junit.jupiter.api.Test;import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class AlertEvidenceHttpTest {
 @Test void protectedStrictSmallRequestDoesNotParseUntrustedInput()throws Exception{
  var service=mock(AlertEvidenceService.class);var mvc=MockMvcBuilders.standaloneSetup(new AlertEvidenceController(service)).setControllerAdvice(new ApiExceptionHandler()).addFilters(new CandidateRequestFilter("fixture",2097152)).build();var url="/internal/v1/market/alert-evidence/prepare";
  mvc.perform(post(url).contentType("application/json").content("{")).andExpect(status().isUnauthorized());
  mvc.perform(post(url).header("X-Service-Token","wrong").contentType("application/json").content("{")).andExpect(status().isUnauthorized());
  mvc.perform(post(url).header("X-Service-Token","fixture").contentType("application/json").content("x".repeat(4097))).andExpect(status().isPayloadTooLarge());
  String valid="{\"operationId\":\"20000000-0000-0000-0000-000000000001\",\"instrumentId\":\"SH600030\",\"rawVersionId\":\"10000000-0000-0000-0000-000000000001\",\"tradeDate\":\"2026-10-09\",\"leaseUntil\":\"2026-10-10T06:01:30Z\"}";
  for(var bad:java.util.List.of(valid.replace("2026-10-09","20261009"),valid.replace("20000000-0000-0000-0000-000000000001","1-1-1-1-1"),valid.replace("\"SH600030\"","123"),valid.replace("} ","}" ).substring(0,valid.length()-1)+",\"extra\":1}"))mvc.perform(post(url).header("X-Service-Token","fixture").contentType("application/json").content(bad)).andExpect(status().isBadRequest());
  verifyNoInteractions(service);
  mvc.perform(post(url).header("X-Service-Token","fixture").contentType("application/json").content(valid)).andExpect(status().isOk());
  mvc.perform(get("/internal/v1/market/alert-evidence/versions/not-a-uuid").header("X-Service-Token","fixture")).andExpect(status().isBadRequest());
 }
}
