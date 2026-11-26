package com.aistock.gateway;
import java.nio.charset.StandardCharsets;import java.util.*;import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class AdminTaskActionProxyHttpTest {
 AdminTaskProxyHttpTest f;String path,op="00000000-0000-0000-0000-000000000002";
 AtomicReference<String> received=new AtomicReference<>();java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
 @BeforeEach void init()throws Exception {
  f=new AdminTaskProxyHttpTest();f.setup();path="/api/v1/admin/tasks/"+f.id+"/cancel";
  f.server.removeContext("/api/v1/");f.server.createContext("/api/v1/",x->{calls.incrementAndGet();f.uri.set(x.getRequestURI().toString());f.headers.set(x.getRequestHeaders());received.set(new String(x.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));var bytes=f.body.get().getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(f.code.get(),bytes.length);x.getResponseBody().write(bytes);x.close();});
 }
 @AfterEach void close()throws Exception {f.close();}
 String request(){return "{\"operationId\":\""+op+"\",\"expectedRevision\":\"0\",\"reasonCode\":\"CANCEL_NOT_NEEDED\"}";}
 String reply(boolean applied){String code=applied?"TASK_CANCEL_APPLIED":"TASK_REVISION_CONFLICT";return "{\"code\":\""+(applied?"OK":code)+"\",\"message\":\"private upstream\",\"data\":{\"operationId\":\""+op+"\",\"taskId\":\""+f.id+"\",\"action\":\"CANCEL\",\"outcome\":\""+(applied?"APPLIED":"REJECTED")+"\",\"resultCode\":\""+code+"\",\"message\":\"private upstream\",\"beforeState\":\"QUEUED\",\"afterState\":\""+(applied?"CANCELLED":"QUEUED")+"\",\"beforeRevision\":\"0\",\"afterRevision\":\""+(applied?"1":"0")+"\",\"requestedAt\":\"2026-10-20T12:00:00Z\",\"finishedAt\":\"2026-10-20T12:00:00Z\"}}";}
 org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder submit(){return post(path).header("Origin","http://127.0.0.1:15173").header("Cookie","AISTOCK_SESSION=synthetic").header("X-Aistock-Admin-Action","1").contentType("application/json").content(request());}
 @Test void precisePostPreservesRawBodyAndCredentialsButStripsForgedIdentity()throws Exception {
  f.body.set(reply(true));f.mvc.perform(submit().header("X-Service-Token","forged").header("X-Role","ADMIN")).andExpect(status().isOk());
  assertEquals(path,f.uri.get());assertEquals(request(),received.get());assertEquals("1",f.headers.get().getFirst("X-Aistock-Admin-Action"));assertNull(f.headers.get().getFirst("X-Service-Token"));assertNull(f.headers.get().getFirst("X-Role"));verifyNoInteractions(f.user);
 }
 @Test void conflictRetainsBoundReceiptButRemovesUpstreamMessages()throws Exception {
  f.code.set(409);f.body.set(reply(false));f.mvc.perform(submit()).andExpect(status().isConflict()).andExpect(jsonPath("$.data.operationId").value(op)).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private upstream"))));
  f.body.set(reply(false).replace(op,"00000000-0000-0000-0000-000000000099"));f.mvc.perform(submit()).andExpect(status().isServiceUnavailable());
 }
 @Test void oversizedDuplicateHeadersAndMissingOriginNeverForward()throws Exception {
  f.mvc.perform(submit().content(" ".repeat(4097))).andExpect(status().isBadRequest());
  f.mvc.perform(submit().header("X-Aistock-Admin-Action","1")).andExpect(status().isForbidden());
  f.mvc.perform(post(path).contentType("application/json").content(request())).andExpect(status().isForbidden());assertNull(f.uri.get());
 }
 @Test void dedicatedHttpTransportNeverRetriesUnavailablePostAndBoundsSuccessBody()throws Exception {
  var config=new SchedulerPublicFeignConfiguration();
  try(var transport=config.schedulerApacheClient()) {
   var client=feign.Feign.builder().contract(new org.springframework.cloud.openfeign.support.SpringMvcContract()).client(new feign.hc5.ApacheHttp5Client(transport)).retryer(feign.Retryer.NEVER_RETRY).decoder(config.schedulerDecoder()).errorDecoder(config.schedulerErrorDecoder()).target(SchedulerPublicClient.class,"http://127.0.0.1:"+f.server.getAddress().getPort());
   f.code.set(503);f.body.set("{}");assertThrows(feign.FeignException.class,()->client.post(path.substring(8),request().getBytes(StandardCharsets.UTF_8),Map.of("Content-Type","application/json")));assertEquals(1,calls.get());
   f.code.set(200);f.body.set(" ".repeat(262145));assertThrows(RuntimeException.class,()->client.get("admin/tasks",new org.springframework.util.LinkedMultiValueMap<>(),Map.of()));assertEquals(2,calls.get());
  }
 }
 @Test void operationReadsAndExactPreflightWorkWithoutSideEffects()throws Exception {
  f.mvc.perform(get("/api/v1/admin/tasks/"+f.id+"/action-options")).andExpect(status().isOk());
  f.mvc.perform(get("/api/v1/admin/tasks/"+f.id+"/operations/"+op)).andExpect(status().isOk());
  f.uri.set(null);f.mvc.perform(options(path).header("Origin","http://127.0.0.1:15173").header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","content-type,x-aistock-admin-action")).andExpect(status().isOk());assertNull(f.uri.get());
 }

 @Test void trainingCancellationReceiptUsesActualTaskSegment()throws Exception {
  path="/api/v1/admin/tasks/training/"+f.id+"/cancel";
  f.body.set(reply(true));
  f.mvc.perform(submit()).andExpect(status().isOk()).andExpect(jsonPath("$.data.taskId").value(f.id));
  assertEquals(path,f.uri.get());
 }
}
