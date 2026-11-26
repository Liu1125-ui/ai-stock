package com.aistock.market.realization;
import com.aistock.common.ApiExceptionHandler;import com.aistock.market.ingestion.CandidateRequestFilter;
import org.junit.jupiter.api.Test;import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;import static org.mockito.Mockito.*;
class RealizationHttpTest {
 @Test void serviceIdentityPrecedesParsingAndBodiesAreBounded()throws Exception{
  var service=mock(RealizationService.class);var mvc=MockMvcBuilders.standaloneSetup(new RealizationController(service)).setControllerAdvice(new ApiExceptionHandler()).addFilters(new CandidateRequestFilter("private-test",2097152)).build();
  mvc.perform(post("/internal/v1/market/realizations/prepare").contentType("application/json").content("bad")).andExpect(status().isUnauthorized());
  mvc.perform(post("/internal/v1/market/realizations/prepare").header("X-Service-Token","private-test").contentType("application/json").content("x".repeat(4097))).andExpect(status().isPayloadTooLarge());
  mvc.perform(post("/internal/v1/market/realizations/prepare").header("X-Service-Token","private-test").contentType("application/json").content("{\"unexpected\":1}")).andExpect(status().isBadRequest());
  mvc.perform(get("/internal/v1/market/realizations/11111111-1111-1111-1111-111111111111")).andExpect(status().isUnauthorized());verifyNoInteractions(service);
 }
}
