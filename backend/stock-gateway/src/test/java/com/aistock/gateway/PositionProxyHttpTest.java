package com.aistock.gateway;
import com.sun.net.httpserver.HttpServer;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.concurrent.atomic.AtomicReference;
import feign.Feign;import org.springframework.cloud.openfeign.support.SpringMvcContract;import org.springframework.http.*;import com.fasterxml.jackson.databind.ObjectMapper;import com.aistock.common.ApiExceptionHandler;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;import org.springframework.test.web.servlet.setup.MockMvcBuilders;
class PositionProxyHttpTest {
 @Test void positionDeleteCarriesVersionAndRequestWhileWatchlistDeleteStaysEmpty()throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var body=new AtomicReference<String>();var method=new AtomicReference<String>();var code=new java.util.concurrent.atomic.AtomicInteger(200);
  server.createContext("/api/v1/",exchange->{method.set(exchange.getRequestMethod());body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));assertNull(exchange.getRequestHeaders().getFirst("X-User-Id"));assertNull(exchange.getRequestHeaders().getFirst("X-Role"));if(exchange.getRequestURI().getPath().contains("/positions/")&&exchange.getRequestMethod().equals("DELETE")){assertEquals("application/json",exchange.getRequestHeaders().getFirst("Content-Type"));assertEquals("AISTOCK_SESSION=synthetic-position-test",exchange.getRequestHeaders().getFirst("Cookie"));assertEquals("http://127.0.0.1:15173",exchange.getRequestHeaders().getFirst("Origin"));assertEquals("synthetic-xsrf",exchange.getRequestHeaders().getFirst("X-XSRF-TOKEN"));}byte[] data="{\"code\":\"FIXTURE\",\"message\":\"fixture\",\"data\":null}".getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(code.get(),data.length);exchange.getResponseBody().write(data);exchange.close();});server.start();
  try{
   var client=Feign.builder().contract(new SpringMvcContract()).client(new GatewayHttpConfiguration().userHttpClient()).decoder((response,type)->ResponseEntity.status(response.status()).contentType(MediaType.APPLICATION_JSON).body(response.body().asInputStream().readAllBytes())).target(UserPublicClient.class,"http://127.0.0.1:"+server.getAddress().getPort());
   var mvc=MockMvcBuilders.standaloneSetup(new UserApiProxyController(client,new ObjectMapper())).setControllerAdvice(new ApiExceptionHandler()).build();
   var payload="{\"requestId\":\"33333333-3333-3333-3333-333333333333\",\"expectedRevision\":\"1\"}";
   mvc.perform(delete("/api/v1/users/me/positions/44444444-4444-4444-4444-444444444444").contentType("application/json").content(payload).header("Cookie","AISTOCK_SESSION=synthetic-position-test").header("X-XSRF-TOKEN","synthetic-xsrf").header("X-User-Id","forged").header("X-Role","ADMIN").header("Origin","http://127.0.0.1:15173")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
   assertEquals("DELETE",method.get());assertEquals(payload,body.get());
   mvc.perform(delete("/api/v1/users/me/watchlist/SH600030")).andExpect(status().isOk());assertEquals("",body.get());
   for(int status:new int[]{404,409}){code.set(status);mvc.perform(get("/api/v1/users/me/positions/operations/33333333-3333-3333-3333-333333333333")).andExpect(status().is(status)).andExpect(jsonPath("$.data").doesNotExist());}
  }finally{server.stop(0);}
 }
 @Test void onlyPrecisePositionMethodsAreAllowed(){var base="/api/v1/users/me/positions";var id="33333333-3333-3333-3333-333333333333";assertTrue(ProxyHeaderPolicy.allowed("GET",base));assertTrue(ProxyHeaderPolicy.allowed("POST",base));assertTrue(ProxyHeaderPolicy.allowed("PUT",base+"/"+id));assertTrue(ProxyHeaderPolicy.allowed("GET",base+"/operations/"+id));assertFalse(ProxyHeaderPolicy.allowed("PATCH",base+"/"+id));assertFalse(ProxyHeaderPolicy.allowed("GET",base+"/someone"));}
 @Test void privatePositionSnapshotRoutesNeverReachUserProxy()throws Exception{
  var client=org.mockito.Mockito.mock(UserPublicClient.class);
  var mvc=MockMvcBuilders.standaloneSetup(new UserApiProxyController(client,new ObjectMapper())).setControllerAdvice(new ApiExceptionHandler()).build();
  for(var route:new String[]{"/internal/v1/positions/snapshot","/internal/v1/positions/revision","/api/v1/users/me/positions/snapshot","/api/v1/users/me/positions/revision"}){
   assertFalse(ProxyHeaderPolicy.allowed("POST",route));
   mvc.perform(post(route).header("X-Service-Token","forged").contentType("application/json").content("{}")).andExpect(status().isNotFound());
  }
  org.mockito.Mockito.verifyNoInteractions(client);
 }
}
