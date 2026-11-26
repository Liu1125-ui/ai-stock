package com.aistock.market.realization;
import com.sun.net.httpserver.HttpServer;import java.net.InetSocketAddress;import java.nio.charset.StandardCharsets;import java.time.*;import java.util.*;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import static com.aistock.market.realization.RealizationRecords.*;
class RealizationCollectorTest {
 private Request request(){return new Request(UUID.randomUUID(),"SH600030",LocalDate.parse("2026-09-21"),List.of(),LocalDate.parse("2026-09-23"),Instant.now().plusSeconds(90));}
 @Test void redirectsOversizeAndUnknownSourceFieldsAreRejected()throws Exception{
  for(var mode:List.of("redirect","oversize","unknown")){
   var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/internal/v1/market/forecast-collect",ex->{assertEquals("private-test",ex.getRequestHeaders().getFirst("X-Service-Token"));ex.getRequestBody().readAllBytes();byte[] body=(mode.equals("oversize")?"x".repeat(131073):"{\"state\":\"AVAILABLE\",\"candidate\":null,\"errorCode\":null,\"unexpected\":true}").getBytes(StandardCharsets.UTF_8);if(mode.equals("redirect")){ex.getResponseHeaders().set("Location","/elsewhere");ex.sendResponseHeaders(302,-1);}else{ex.sendResponseHeaders(200,body.length);ex.getResponseBody().write(body);}ex.close();});server.start();
   try{var client=new RealizationSourceClient("http://127.0.0.1:"+server.getAddress().getPort(),"private-test",Duration.ofSeconds(2));assertNotEquals("AVAILABLE",client.collect(request(),new Head(UUID.randomUUID().toString(),"a".repeat(64),List.of())).state());}finally{server.stop(0);}
  }
 }
 @Test void timeoutIncludesTheResponseBody()throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/internal/v1/market/forecast-collect",ex->{ex.getRequestBody().readAllBytes();ex.sendResponseHeaders(200,0);ex.getResponseBody().write('{');ex.getResponseBody().flush();try{Thread.sleep(3000);}catch(InterruptedException e){Thread.currentThread().interrupt();}ex.close();});server.start();
  try{long start=System.nanoTime();var result=new RealizationSourceClient("http://127.0.0.1:"+server.getAddress().getPort(),"private-test",Duration.ofSeconds(1)).collect(request(),new Head(UUID.randomUUID().toString(),"a".repeat(64),List.of()));assertEquals("SOURCE_TIMEOUT",result.state());assertTrue(Duration.ofNanos(System.nanoTime()-start).toMillis()<2500);}finally{server.stop(0);}
 }
}
