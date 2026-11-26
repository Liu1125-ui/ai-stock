package com.aistock.gateway;
import com.aistock.common.ApiExceptionHandler;import com.fasterxml.jackson.databind.ObjectMapper;import java.util.*;import org.junit.jupiter.api.Test;import org.mockito.ArgumentCaptor;import org.springframework.http.ResponseEntity;import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;import static org.junit.jupiter.api.Assertions.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class ForecastProxyTest {
 @Test void publicForecastForwardsOnlyAllowedQueryAndBrowserHeaders()throws Exception{var client=mock(ForecastPublicClient.class);when(client.get(anyString(),anyMap(),anyMap())).thenReturn(ResponseEntity.ok("{\"code\":\"OK\",\"data\":{}}".getBytes()));var mvc=MockMvcBuilders.standaloneSetup(new ForecastApiProxyController(client,new ObjectMapper())).setControllerAdvice(new ApiExceptionHandler()).build();mvc.perform(get("/api/v1/predictions").param("page","2").param("ownerId","forged").header("Cookie","AISTOCK_SESSION=fixture").header("X-Service-Token","forged")).andExpect(status().isOk());var headers=ArgumentCaptor.forClass(Map.class);var query=ArgumentCaptor.forClass(Map.class);verify(client).get(eq("predictions"),query.capture(),headers.capture());assertEquals(Map.of("page","2"),query.getValue());assertFalse(headers.getValue().containsKey("X-Service-Token"));mvc.perform(get("/internal/v1/predictions")).andExpect(status().isNotFound());assertFalse(ProxyHeaderPolicy.allowed("DELETE","/api/v1/predictions/00000000-0000-0000-0000-000000000001"));}

 @Test void trainingRoutesPreserveOnlyTheHistoryCursor() throws Exception {
  var client=mock(ForecastPublicClient.class);
  when(client.get(anyString(),anyMap(),anyMap())).thenReturn(ResponseEntity.ok("{}".getBytes()));
  var mvc=MockMvcBuilders.standaloneSetup(new ForecastApiProxyController(client,new ObjectMapper()))
      .setControllerAdvice(new ApiExceptionHandler()).build();
  String path="/api/v1/training-submissions", id="00000000-0000-0000-0000-000000000001";
  mvc.perform(get(path).param("before",id)).andExpect(status().isOk());
  verify(client).get(eq("training-submissions"),eq(Map.of("before",id)),anyMap());
  mvc.perform(get(path).param("before",id,id)).andExpect(status().isBadRequest());
  mvc.perform(get(path+"/"+id+"/status").param("ownerId","forged")).andExpect(status().isBadRequest());
  mvc.perform(get(path+"/"+id+"/tasks/"+id+"/metrics")).andExpect(status().isOk());
  mvc.perform(post(path).contentType("application/json").content("x".repeat(2049)))
      .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("TRAINING_REQUEST_INVALID"));
  verify(client).get(eq("training-submissions/"+id+"/tasks/"+id+"/metrics"),eq(Map.of()),anyMap());
  verifyNoMoreInteractions(client);
 }

 @Test void fusionPreviewForwardsBrowserCredentialsAndKeepsResponsesPrivate()throws Exception {
  var client=mock(ForecastPublicClient.class);
  when(client.post(anyString(),anyMap(),any(byte[].class))).thenReturn(ResponseEntity.ok("{}".getBytes()));
  var mvc=MockMvcBuilders.standaloneSetup(new ForecastApiProxyController(client,new ObjectMapper()))
      .setControllerAdvice(new ApiExceptionHandler()).build();
  mvc.perform(post("/api/v1/predictions/fusion-preview").contentType("application/json")
      .header("Cookie","AISTOCK_SESSION=fixture").header("X-XSRF-TOKEN","csrf").content("{}"))
      .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
  verify(client).post(eq("predictions/fusion-preview"),argThat(h->
      "AISTOCK_SESSION=fixture".equals(h.get("Cookie"))&&"csrf".equals(h.get("X-XSRF-TOKEN"))),any(byte[].class));
 }

 @Test void latestByInstrumentForwardsOnlyTheLimitedStockList() throws Exception {
  var client=mock(ForecastPublicClient.class);
  when(client.get(anyString(),anyMap(),anyMap())).thenReturn(ResponseEntity.ok("[]".getBytes()));
  var mvc=MockMvcBuilders.standaloneSetup(new ForecastApiProxyController(client,new ObjectMapper()))
      .setControllerAdvice(new ApiExceptionHandler()).build();
  String path="/api/v1/predictions/latest-by-instrument";
  mvc.perform(get(path).param("ids","SH600030,SZ000002")).andExpect(status().isOk());
  mvc.perform(get(path).param("ids","SH600030","SZ000002")).andExpect(status().isBadRequest());
  mvc.perform(get(path).param("ids","SH600030").param("ownerId","forged")).andExpect(status().isBadRequest());
  mvc.perform(post(path)).andExpect(status().isNotFound());
  verify(client).get(eq("predictions/latest-by-instrument"),eq(Map.of("ids","SH600030,SZ000002")),anyMap());
  verifyNoMoreInteractions(client);
 }
}
