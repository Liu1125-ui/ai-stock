package com.aistock.notification.alert;
import com.aistock.common.BusinessException;import com.fasterxml.jackson.databind.*;import com.sun.net.httpserver.HttpServer;import java.net.*;import java.time.*;import java.util.*;import java.nio.charset.StandardCharsets;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class AlertEvidenceClientTest {
 final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
 JsonNode fixture()throws Exception{return mapper.readTree(getClass().getResourceAsStream("/alert/evidence.json"));}
 @Test void readsActualEvidenceAndRejectsTamperedHashOrIdentity()throws Exception{
  var body=new String[]{mapper.writeValueAsString(Map.of("code","OK","message","成功","data",fixture(),"traceId","fixture"))};var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",x->{assertEquals("fixture-token",x.getRequestHeaders().getFirst("X-Service-Token"));byte[] bytes=body[0].getBytes(StandardCharsets.UTF_8);x.sendResponseHeaders(200,bytes.length);x.getResponseBody().write(bytes);x.close();});server.start();
  try{var http=new AlertRemoteHttp("http://127.0.0.1:"+server.getAddress().getPort(),"fixture-token");var clock=Clock.fixed(Instant.parse(fixture().get("capturedAt").asText()).plusSeconds(1),ZoneOffset.UTC);var client=new AlertEvidenceClient(http,clock);var id=UUID.fromString(fixture().get("id").asText());
   assertEquals("25.91",client.read(id).rawClose());
   assertThrows(BusinessException.class,()->client.read(UUID.randomUUID()));
   var bad=fixture().deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)bad).put("rawClose","999");body[0]=mapper.writeValueAsString(Map.of("code","OK","data",bad,"message","成功","traceId","fixture"));assertThrows(BusinessException.class,()->client.read(id));
   ((com.fasterxml.jackson.databind.node.ObjectNode)bad).put("rawClose",25.91);body[0]=mapper.writeValueAsString(Map.of("code","OK","data",bad,"message","成功","traceId","fixture"));assertThrows(BusinessException.class,()->client.read(id));
  }finally{server.stop(0);}
 }
 @Test void accountStatusChecksIdentityFreshnessAndDependencyFailures()throws Exception{
  var user=UUID.randomUUID();var now=Instant.parse("2026-10-10T09:00:00Z");var body=new String[]{""};var status=new int[]{200};var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",x->{byte[] bytes=body[0].getBytes(StandardCharsets.UTF_8);x.sendResponseHeaders(status[0],bytes.length);x.getResponseBody().write(bytes);x.close();});server.start();
  try{var client=new AlertAccountClient(new AlertRemoteHttp("http://127.0.0.1:"+server.getAddress().getPort(),"fixture-token"),Clock.fixed(now,ZoneOffset.UTC));
   for(String state:List.of("ENABLED","DISABLED")){body[0]=mapper.writeValueAsString(Map.of("code","OK","message","成功","traceId","fixture","data",Map.of("userId",user.toString(),"status",state,"checkedAt",now.toString())));assertEquals(state,client.status(user).status());}
   assertThrows(BusinessException.class,()->client.status(UUID.randomUUID()));
   body[0]=body[0].replace(now.toString(),now.minusSeconds(6).toString());assertThrows(BusinessException.class,()->client.status(user));
   status[0]=503;assertEquals("ALERT_DEPENDENCY_UNAVAILABLE",assertThrows(BusinessException.class,()->client.status(user)).code());
   status[0]=200;body[0]="x".repeat(131073);assertThrows(BusinessException.class,()->client.status(user));
  }finally{server.stop(0);}
 }
 @Test void headerArrivalDoesNotDisableTheResponseBodyDeadline()throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(r->{var t=new Thread(r);t.setDaemon(true);return t;}));
  server.createContext("/",x->{try{x.sendResponseHeaders(200,0);x.getResponseBody().write('{');x.getResponseBody().flush();Thread.sleep(40000);}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{x.close();}});server.start();
  try{var http=new AlertRemoteHttp("http://127.0.0.1:"+server.getAddress().getPort(),"fixture-token");assertTimeoutPreemptively(Duration.ofSeconds(33),()->assertThrows(BusinessException.class,()->http.get("stock-user-service","/internal/v1/users/fixture/alert-status")));}finally{server.stop(0);}
 }
 @Test void boundaryUsesFreshDatesAndRejectsFutureCutoff()throws Exception{
  var now=Instant.parse("2026-10-10T09:00:00Z");var b=new AlertRecords.Boundary("SH600030","baostock",UUID.randomUUID(),LocalDate.parse("2026-10-09"),LocalDate.parse("2026-10-09"),LocalDate.parse("2026-10-09"),"a".repeat(64),now,"alert-boundary-v1");var body=new String[]{mapper.writeValueAsString(Map.of("code","OK","data",b))};var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",x->{byte[] bytes=body[0].getBytes(StandardCharsets.UTF_8);x.sendResponseHeaders(200,bytes.length);x.getResponseBody().write(bytes);x.close();});server.start();
  try{var client=new AlertEvidenceClient(new AlertRemoteHttp("http://127.0.0.1:"+server.getAddress().getPort(),"fixture-token"),Clock.fixed(now,ZoneOffset.UTC));assertEquals(b,client.boundary("SH600030"));body[0]=body[0].replace(now.toString(),now.minusSeconds(6).toString());assertThrows(BusinessException.class,()->client.boundary("SH600030"));body[0]=mapper.writeValueAsString(Map.of("code","OK","data",b)).replace("2026-10-09","2026-10-11");assertThrows(BusinessException.class,()->client.boundary("SH600030"));}finally{server.stop(0);}
 }
}
