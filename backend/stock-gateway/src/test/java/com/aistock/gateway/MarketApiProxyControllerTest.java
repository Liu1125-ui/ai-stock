package com.aistock.gateway;
import com.aistock.common.ApiExceptionHandler;import com.fasterxml.jackson.databind.ObjectMapper;import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;import static org.junit.jupiter.api.Assertions.*;
class MarketApiProxyControllerTest {
 @Test void queryHeadersAreRestrictedAndOnlyAllowedPathsProxy()throws Exception{var c=mock(MarketPublicClient.class);when(c.get(anyString(),anyMap(),anyMap())).thenAnswer(call->{java.util.Map<String,String> h=call.getArgument(2),q=call.getArgument(1);assertFalse(h.containsKey("X-User-Id"));assertFalse(h.containsKey("X-Service-Token"));assertFalse(q.containsKey("userId"));return ResponseEntity.ok("{}".getBytes());});var mvc=MockMvcBuilders.standaloneSetup(new MarketApiProxyController(c,new ObjectMapper())).setControllerAdvice(new ApiExceptionHandler()).build();mvc.perform(get("/api/v1/instruments/SH600030/daily").param("basis","RAW").param("userId","forged").header("X-User-Id","forged")).andExpect(status().isOk());mvc.perform(get("/api/v1/instruments/SH600030/internal")).andExpect(status().isNotFound());mvc.perform(post("/api/v1/instruments")).andExpect(status().isNotFound());}
 @Test void unreachableMarketDoesNotProduceSuccessData()throws Exception{var c=mock(MarketPublicClient.class);when(c.get(anyString(),anyMap(),anyMap())).thenThrow(new IllegalStateException());var mvc=MockMvcBuilders.standaloneSetup(new MarketApiProxyController(c,new ObjectMapper())).setControllerAdvice(new ApiExceptionHandler()).build();mvc.perform(get("/api/v1/instruments")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.data").doesNotExist());}
 @Test void marketOverviewOnlyProxiesRead() throws Exception {
  var client=mock(MarketPublicClient.class);
  when(client.get(eq("instruments/market-overview"),anyMap(),anyMap())).thenReturn(ResponseEntity.ok("{}".getBytes()));
  var mvc=MockMvcBuilders.standaloneSetup(new MarketApiProxyController(client,new ObjectMapper()))
          .setControllerAdvice(new ApiExceptionHandler()).build();
  mvc.perform(get("/api/v1/instruments/market-overview")).andExpect(status().isOk());
  mvc.perform(post("/api/v1/instruments/market-overview")).andExpect(status().isNotFound());
  verify(client,times(1)).get(eq("instruments/market-overview"),anyMap(),anyMap());
 }
 @Test void pilotAllowsDetailDailyAndWatchlistRemovalOnlyOnSupportedRoutes() {
  assertTrue(ProxyHeaderPolicy.allowed("GET","/api/v1/instruments/HK00700"));
  assertTrue(ProxyHeaderPolicy.allowed("GET","/api/v1/instruments/HK00700/daily"));
  assertTrue(ProxyHeaderPolicy.allowed("GET","/api/v1/instruments/HK00700/realtime"));
  assertFalse(ProxyHeaderPolicy.allowed("POST","/api/v1/instruments/HK00700/realtime"));
  assertTrue(ProxyHeaderPolicy.allowed("DELETE","/api/v1/users/me/watchlist/HK00700"));
  assertFalse(ProxyHeaderPolicy.allowed("GET","/api/v1/instruments/HK00700/valuation"));
  assertFalse(ProxyHeaderPolicy.allowed("GET","/api/v1/instruments/HK00941"));
 }
}
