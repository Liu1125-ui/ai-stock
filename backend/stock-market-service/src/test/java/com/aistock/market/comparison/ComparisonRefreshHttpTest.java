package com.aistock.market.comparison;
import com.aistock.common.*;
import com.aistock.market.ingestion.CandidateRequestFilter;
import com.fasterxml.jackson.databind.*;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static com.aistock.market.comparison.ComparisonUpdateSamples.*;
class ComparisonRefreshHttpTest {
 @Test void identitySizeAndStrictJsonAreCheckedBeforeExecution()throws Exception{
  var s=mock(ComparisonRefreshService.class);var repo=mock(ComparisonRefreshRepository.class);
  var mvc=MockMvcBuilders.standaloneSetup(new ComparisonRefreshController(s,repo)).setControllerAdvice(new ApiExceptionHandler())
   .addFilters(new CandidateRequestFilter("fixture",2097152)).build();
  var r=new ComparisonUpdateRecords.Request(UUID.randomUUID(),"SH600030",UUID.randomUUID(),"a".repeat(64),START,END,ComparisonEvidenceValidator.DEFINITION,NOW.instant().plusSeconds(90));
  var json=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).writeValueAsString(r);
  for(var path:List.of("/internal/v1/market/comparison-refresh","/internal/v1/market;x/comparison-refresh;x")){
   mvc.perform(post(path).contentType("application/json").content("{")).andExpect(status().isUnauthorized());
   mvc.perform(post(path).header("X-Service-Token","fixture").contentType("application/json").content("x".repeat(4097))).andExpect(status().isPayloadTooLarge());
  }
  for(var body:List.of(json+" {}",json.replace("\"instrumentId\":\"SH600030\"","\"instrumentId\":\"SH600030\",\"instrumentId\":\"SH600030\""),
    json.replace("\"instrumentId\":\"SH600030\"","\"extra\":1,\"instrumentId\":\"SH600030\""),
    json.replace(r.rawVersionId().toString(),"1-1-1-1-1"))){
   mvc.perform(post("/internal/v1/market/comparison-refresh").header("X-Service-Token","fixture").contentType("application/json").content(body))
    .andExpect(status().isUnprocessableEntity());
  }
  verifyNoInteractions(s,repo);
  mvc.perform(post("/internal/v1/market/comparison-refresh").header("X-Service-Token","fixture").contentType("application/json").content(json))
   .andExpect(status().isOk());verify(s).refresh(r);
 }
 @Test void lookupIsReadOnlyAndRejectsUnknownParameters()throws Exception{
  var s=mock(ComparisonRefreshService.class);var repo=mock(ComparisonRefreshRepository.class);var op=UUID.randomUUID();
  when(repo.lookup(op)).thenThrow(new BusinessException("COMPARISON_REFRESH_NOT_FOUND","未登记",org.springframework.http.HttpStatus.NOT_FOUND));
  var mvc=MockMvcBuilders.standaloneSetup(new ComparisonRefreshController(s,repo)).setControllerAdvice(new ApiExceptionHandler())
   .addFilters(new CandidateRequestFilter("fixture",4096)).build();
  mvc.perform(get("/internal/v1/market/comparison-refresh/"+op).header("X-Service-Token","fixture")).andExpect(status().isNotFound());
  mvc.perform(get("/internal/v1/market/comparison-refresh/"+op).header("X-Service-Token","fixture").param("extra","x")).andExpect(status().isUnprocessableEntity());
  verify(repo,times(1)).lookup(op);verifyNoInteractions(s);
 }
}
