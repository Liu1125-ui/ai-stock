package com.aistock.market.forecast;
import com.aistock.market.ingestion.CandidateRequestFilter;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
class ForecastInputHttpTest {
 @Test void mappedMatrixRoutesRequireIdentityAndBoundBodiesBeforeControllers()throws Exception {
  var service=mock(ForecastInputService.class);var mvc=MockMvcBuilders.standaloneSetup(new ForecastInputController(service)).addFilters(new CandidateRequestFilter("service-private",64)).build();
  for(var root:new String[]{"/internal/v1/market/","/internal/v1/market;v/"}){
   for(var name:new String[]{"forecast-window","forecast-raw-window"}){
    mvc.perform(get(root+name+";x").param("instrumentId","SH600030")).andExpect(status().isUnauthorized());
   }
   mvc.perform(post(root+"forecast-inputs;x").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
   mvc.perform(post(root+"forecast-inputs;x").header("X-Service-Token","service-private").contentType("application/json").content("x".repeat(65))).andExpect(status().isPayloadTooLarge());
  }
  verifyNoInteractions(service);
  mvc.perform(get("/internal/v1/market/forecast-raw-window;x").param("instrumentId","SH600030").header("X-Service-Token","service-private")).andExpect(status().isOk());
  verify(service).rawWindow("SH600030");
 }
}
