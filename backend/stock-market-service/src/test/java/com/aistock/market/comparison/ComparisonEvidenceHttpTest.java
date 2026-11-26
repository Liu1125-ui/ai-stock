package com.aistock.market.comparison;
import com.aistock.market.ingestion.CandidateRequestFilter;
import com.aistock.common.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
class ComparisonEvidenceHttpTest {
 @Test void internalRoutesAuthenticateBeforeParsingAndBoundBodies()throws Exception {
  var s=mock(ComparisonEvidenceService.class);
  var mvc=MockMvcBuilders.standaloneSetup(new ComparisonEvidenceController(s))
    .setControllerAdvice(new ApiExceptionHandler()).addFilters(new CandidateRequestFilter("fixture",64)).build();
  for(var root:new String[]{"/internal/v1/market/","/internal/v1/market;x/"}) {
   mvc.perform(get(root+"comparison-raw-window;x")).andExpect(status().isUnauthorized());
   mvc.perform(get(root+"comparison-evidence/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa;x")).andExpect(status().isUnauthorized());
   mvc.perform(post(root+"comparison-evidence;x").contentType("application/json").content("{"))
     .andExpect(status().isUnauthorized());
   mvc.perform(post(root+"comparison-evidence;x").header("X-Service-Token","fixture")
     .contentType("application/json").content("x".repeat(65))).andExpect(status().isPayloadTooLarge());
  }
  mvc.perform(get("/internal/v1/market/comparison-options")).andExpect(status().isUnauthorized());
  mvc.perform(get("/internal/v1/market;x/comparison-options;x")).andExpect(status().isUnauthorized());
  verifyNoInteractions(s);
  mvc.perform(get("/internal/v1/market/comparison-options").header("X-Service-Token","fixture").param("ids","SH600030,SH600276").param("period","3M").param("extra","x")).andExpect(status().isUnprocessableEntity());

  mvc.perform(get("/internal/v1/market/comparison-raw-window").header("X-Service-Token","fixture")
    .param("instrumentId","SH600030").param("from","2026-10-08").param("to","2026-10-09"))
    .andExpect(status().isOk());
  verify(s).rawWindow("SH600030",java.time.LocalDate.of(2026,10,8),java.time.LocalDate.of(2026,10,9));
  mvc.perform(get("/internal/v1/market/comparison-raw-window").header("X-Service-Token","fixture")
    .param("instrumentId","SH600030").param("from","2026-10-08").param("to","2026-10-09").param("extra","x"))
    .andExpect(status().isUnprocessableEntity());
 }
}
