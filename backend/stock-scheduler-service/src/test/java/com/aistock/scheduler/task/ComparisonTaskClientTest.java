package com.aistock.scheduler.task;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.*;import java.net.*;import java.nio.charset.StandardCharsets;
import java.time.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
import static com.aistock.scheduler.task.ComparisonTaskMysqlTest.*;
class ComparisonTaskClientTest {
 static final ObjectMapper MAPPER=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
 @Test void realHttpOptionsExecutionAndReadUseStrictBoundedContract()throws Exception{
  var op=UUID.randomUUID().toString();var task=UUID.randomUUID().toString();
  var claim=new TaskRepository.Claim(task,op,"SH600030",DAY,NOW.plusSeconds(90),"COMPARISON_UPDATE",null,null);
  var ctx=new ComparisonTaskRecords.Context(task,DAY,snapshot(NOW),3);
  var options=new ComparisonTaskRecords.Options(NOW,"baostock-comparison-backward-close-v1",List.of(ComparisonUpdatePlanTest.item("READY")));
  var optionJson=MAPPER.writeValueAsString(com.aistock.common.ApiResponse.success(options,"t"));
  var executionJson=MAPPER.writeValueAsString(com.aistock.common.ApiResponse.success(new MarketRefreshClient.Result(op,"AVAILABLE",UUID.randomUUID().toString(),null),"t"));
  var body=new AtomicReference<>(optionJson);var status=new AtomicInteger(200);var sent=new AtomicReference<String>();var token=new AtomicReference<String>();
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",e->{sent.set(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));token.set(e.getRequestHeaders().getFirst("X-Service-Token"));
   var b=body.get().getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(status.get(),b.length);try{e.getResponseBody().write(b);}finally{e.close();}});server.start();
  try{
   var c=new HttpComparisonTaskClient("http://127.0.0.1:"+server.getAddress().getPort(),"fixture",MAPPER,Clock.fixed(NOW,ZoneOffset.UTC));
   assertEquals("READY",c.options(List.of("SH600030")).items().get(0).state());assertEquals("fixture",token.get());
   for(var bad:List.of(optionJson+" {}",optionJson.replace("\"asOf\":","\"extra\":0,\"asOf\":"),
     optionJson.replace("READY","UNKNOWN"),optionJson.replace(NOW.toString(),NOW.plusSeconds(6).toString()),"x".repeat(262145))){
    body.set(bad);assertThrows(ComparisonTaskHttp.Remote.class,()->c.options(List.of("SH600030")));
   }
   body.set(executionJson);assertEquals("AVAILABLE",c.execute(claim,ctx).state());
   var root=MAPPER.readTree(sent.get());var fields=new HashSet<String>();root.fieldNames().forEachRemaining(fields::add);
   assertEquals(Set.of("operationId","instrumentId","rawVersionId","rawContentHash","start","end","definition","leaseUntil"),fields);
   assertEquals(op,root.path("operationId").asText());assertEquals(claim.leaseUntil().toString(),root.path("leaseUntil").asText());assertFalse(sent.get().contains("fixture"));
   assertEquals("AVAILABLE",c.receipt(claim).state());assertEquals("",sent.get());
   body.set(executionJson.replace(op,UUID.randomUUID().toString()));assertThrows(ComparisonTaskHttp.Remote.class,()->c.receipt(claim));
   body.set(executionJson.replace("\"state\":\"AVAILABLE\"","\"state\":\"AVAILABLE\",\"state\":\"AVAILABLE\""));assertThrows(ComparisonTaskHttp.Remote.class,()->c.receipt(claim));
   body.set("{\"code\":\"COMPARISON_REFRESH_NOT_FOUND\",\"message\":\"none\",\"data\":null,\"traceId\":\"t\"}");status.set(404);
   assertThrows(ComparisonTaskClient.MissingReceipt.class,()->c.receipt(claim));
   for(int s:List.of(301,500)){status.set(s);assertThrows(ComparisonTaskHttp.Remote.class,()->c.receipt(claim));}
  }finally{server.stop(0);}
 }
 @Test void slowBodyBoundIsIndependentFromOtherClients()throws Exception{
  var release=new CountDownLatch(1);var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",e->{e.getRequestBody().readAllBytes();e.sendResponseHeaders(200,0);e.getResponseBody().write('{');e.getResponseBody().flush();
   try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException x){Thread.currentThread().interrupt();}finally{e.close();}});server.start();
  try{
   var c=new HttpComparisonTaskClient("http://127.0.0.1:"+server.getAddress().getPort(),"fixture",MAPPER,Clock.fixed(NOW,ZoneOffset.UTC),
    Duration.ofMillis(200),Duration.ofMillis(200),Duration.ofMillis(200));
   var start=System.nanoTime();assertThrows(ComparisonTaskHttp.Remote.class,()->c.options(List.of("SH600030")));
   assertTrue(Duration.ofNanos(System.nanoTime()-start).toMillis()<1800);
  }finally{release.countDown();server.stop(0);}
 }
 @Test void deniedFrozenRawDoesNotSwitchVersionOrRetry()throws Exception{var task=UUID.randomUUID().toString();var claim=new TaskRepository.Claim(task,UUID.randomUUID().toString(),"SH600030",DAY,NOW.plusSeconds(90),"COMPARISON_UPDATE",null,null);var ctx=new ComparisonTaskRecords.Context(task,DAY,snapshot(NOW),3);var calls=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",e->{calls.incrementAndGet();var body=MAPPER.readTree(e.getRequestBody());assertEquals(ctx.snapshot().rawVersionId().toString(),body.path("rawVersionId").asText());assertEquals(claim.attemptId(),body.path("operationId").asText());byte[] out="{\"code\":\"MARKET_VERSION_USE_BLOCKED\",\"data\":null}".getBytes();e.sendResponseHeaders(409,out.length);e.getResponseBody().write(out);e.close();});server.start();try{var client=new HttpComparisonTaskClient("http://127.0.0.1:"+server.getAddress().getPort(),"private",MAPPER,Clock.fixed(NOW,ZoneOffset.UTC));var result=client.execute(claim,ctx);assertEquals("FAILED",result.state());assertEquals("MARKET_VERSION_USE_BLOCKED",result.errorCode());assertEquals(1,calls.get());}finally{server.stop(0);}}

}
