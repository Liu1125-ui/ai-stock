package com.aistock.market.comparison;
import com.aistock.common.*;
import com.aistock.market.ingestion.CandidateRequestFilter;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

class ComparisonUpdateHttpTest {
 @Test void normalizedInternalRoutesAuthenticateBeforeParameters()throws Exception{
  var q=mock(ComparisonUpdateQuery.class);
  var mvc=MockMvcBuilders.standaloneSetup(new ComparisonUpdateController(q))
   .setControllerAdvice(new ApiExceptionHandler()).addFilters(new CandidateRequestFilter("fixture",4096)).build();
  for(var path:new String[]{"/internal/v1/market/comparison-update-options","/internal/v1/market;x/comparison-update-options;x"}){
   mvc.perform(get(path).param("ids","garbage")).andExpect(status().isUnauthorized());
  }
  verifyNoInteractions(q);
 }
 @Test void queryAllowsOnlySingleIdsAndValidUniqueStocks()throws Exception{
  var q=mock(ComparisonUpdateQuery.class);
  var mvc=MockMvcBuilders.standaloneSetup(new ComparisonUpdateController(q))
   .setControllerAdvice(new ApiExceptionHandler()).addFilters(new CandidateRequestFilter("fixture",4096)).build();
  for(var ids:new String[]{"","SH600030,SH600030","SH600030,","BAD","SH600030,".repeat(51)}){
   mvc.perform(get("/internal/v1/market/comparison-update-options").header("X-Service-Token","fixture").param("ids",ids))
    .andExpect(status().isUnprocessableEntity());
  }
  mvc.perform(get("/internal/v1/market/comparison-update-options").header("X-Service-Token","fixture")
   .param("ids","SH600030","SH600276")).andExpect(status().isUnprocessableEntity());
  mvc.perform(get("/internal/v1/market/comparison-update-options").header("X-Service-Token","fixture")
   .param("ids","SH600030").param("extra","x")).andExpect(status().isUnprocessableEntity());
  verifyNoInteractions(q);
  mvc.perform(get("/internal/v1/market/comparison-update-options").header("X-Service-Token","fixture")
   .param("ids","SH600030,SH600276")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("OK"));
  verify(q).options(java.util.List.of("SH600030","SH600276"));
 }
}
