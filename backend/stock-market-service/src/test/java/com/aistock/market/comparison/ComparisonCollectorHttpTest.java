package com.aistock.market.comparison;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.*;
import java.net.*;import java.time.*;import java.util.*;import java.nio.charset.StandardCharsets;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.aistock.market.comparison.ComparisonUpdateSamples.*;
class ComparisonCollectorHttpTest {
 static final ObjectMapper MAPPER=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
 static ComparisonUpdateRecords.Request request(){
  return new ComparisonUpdateRecords.Request(UUID.randomUUID(),"SH600030",UUID.randomUUID(),"a".repeat(64),START,END,ComparisonEvidenceValidator.DEFINITION,NOW.instant().plusSeconds(90));
 }
 @Test void boundedProtocolAndFiveFieldRequest()throws Exception{
  var r=request();var body=new AtomicReference<>(MAPPER.writeValueAsString(new ComparisonCollector.Collected("AVAILABLE",candidate(r,"10","20",NOW),null)));
  var sent=new AtomicReference<String>();var token=new AtomicReference<String>();
  var status=new AtomicInteger(200);var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/internal/v1/market/comparison-collect",e->{
   sent.set(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));token.set(e.getRequestHeaders().getFirst("X-Service-Token"));
   var b=body.get().getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(status.get(),b.length);try{e.getResponseBody().write(b);}finally{e.close();}
  });server.start();
  try{
   var c=new ComparisonCollectorClient("http://127.0.0.1:"+server.getAddress().getPort(),"fixture",MAPPER);
   assertEquals("AVAILABLE",c.collect(r).state());
   var root=MAPPER.readTree(sent.get());var names=new HashSet<String>();root.fieldNames().forEachRemaining(names::add);
   assertEquals(Set.of("operationId","instrumentId","rawVersionId","start","end"),names);assertEquals("fixture",token.get());
   assertFalse(sent.get().contains("fixture"));assertEquals(r.operationId().toString(),root.path("operationId").asText());
   for(var code:List.of("SOURCE_TIMEOUT","SOURCE_BUSY","SOURCE_UNAVAILABLE","SOURCE_INVALID")){
    body.set(MAPPER.writeValueAsString(new ComparisonCollector.Collected(code,null,code)));
    assertEquals(code,c.collect(r).errorCode());
   }
   var valid=MAPPER.writeValueAsString(new ComparisonCollector.Collected("AVAILABLE",candidate(r,"10","20",NOW),null));
   for(var invalid:List.of(valid+" {}",valid.replace("\"state\":\"AVAILABLE\"","\"state\":\"AVAILABLE\",\"state\":\"AVAILABLE\""),
     valid.replace("\"state\":\"AVAILABLE\"","\"extra\":0,\"state\":\"AVAILABLE\""),
     "{\"state\":\"UNKNOWN\",\"candidate\":null,\"errorCode\":null}",
     "{\"state\":\"SOURCE_BUSY\",\"candidate\":null,\"errorCode\":\"SOURCE_TIMEOUT\"}","x".repeat(2097153))){
    body.set(invalid);assertEquals("SOURCE_INVALID",c.collect(r).errorCode());
   }
   var wrong=candidate(r,"10","20",NOW);
   body.set(MAPPER.writeValueAsString(new ComparisonCollector.Collected("AVAILABLE",
    new ComparisonEvidenceRecords.Candidate("wrong",wrong.instrumentId(),wrong.rawVersionId(),wrong.from(),wrong.to(),wrong.provider(),
     wrong.definition(),wrong.fetchedAt(),wrong.sourcePayloadHash(),wrong.points(),wrong.calendar()),null)));
   assertEquals("SOURCE_INVALID",c.collect(r).errorCode());
   body.set(valid);
   for(int http:List.of(301,401,500)){status.set(http);assertEquals("SOURCE_UNAVAILABLE",c.collect(r).errorCode());}
  }finally{server.stop(0);}
 }
 @Test void deadlineIncludesSlowResponseBody()throws Exception{
  var release=new CountDownLatch(1);var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",e->{e.getRequestBody().readAllBytes();e.sendResponseHeaders(200,0);e.getResponseBody().write('{');e.getResponseBody().flush();
   try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException x){Thread.currentThread().interrupt();}finally{e.close();}});
  server.start();
  try{
   var c=new ComparisonCollectorClient("http://127.0.0.1:"+server.getAddress().getPort(),"fixture",MAPPER,Duration.ofMillis(200));
   var begin=System.nanoTime();assertEquals("SOURCE_TIMEOUT",c.collect(request()).errorCode());
   assertTrue(Duration.ofNanos(System.nanoTime()-begin).toMillis()<1800);
  }finally{release.countDown();server.stop(0);}
 }
}
