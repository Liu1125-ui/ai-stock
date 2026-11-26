package com.aistock.notification.alert;

import com.aistock.common.BusinessException;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class AlertMailAccountClientTest {
 final UUID id=UUID.fromString("11111111-1111-4111-8111-111111111111");
 final Instant now=Instant.parse("2026-10-12T08:00:00Z");
 HttpServer server; String body; int code=200; String receivedPath;
 AlertMailAccountClient client;
 String payload(boolean target){return "{\"code\":\"OK\",\"data\":{\"userId\":\""+id+"\",\"status\":\"ENABLED\",\"revision\":2,\"emailVerified\":true,"+(target?"\"email\":\"fixture@example.com\",":"")+"\"checkedAt\":\""+now+"\"}}";}
 @BeforeEach void setup()throws Exception {
  body=payload(false); server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",x->{receivedPath=x.getRequestURI().toString();assertEquals("fixture",x.getRequestHeaders().getFirst("X-Service-Token"));byte[] bytes=body.getBytes(StandardCharsets.UTF_8);x.sendResponseHeaders(code,bytes.length);x.getResponseBody().write(bytes);x.close();}); server.start();
  client=new AlertMailAccountClient("http://127.0.0.1:"+server.getAddress().getPort(),"fixture",Clock.fixed(now,ZoneOffset.UTC));
 }
 @AfterEach void stop(){server.stop(0);}
 @Test void readsStrictContextAndRevisionBoundTarget() {
  var context=client.context(id);assertEquals(2,context.revision());assertTrue(context.emailVerified());assertEquals("/internal/v1/users/"+id+"/alert-context",receivedPath);
  body=payload(true);var target=client.target(id,2);assertEquals("fixture@example.com",target.email());assertEquals("/internal/v1/users/"+id+"/alert-mail-target?revision=2",receivedPath);
  assertFalse(target.toString().contains("fixture@example.com"));
 }
 @Test void rejectsWrongIdentityTypesRevisionsUnknownOrRepeatedFieldsAndFreshness() {
  for(String bad:List.of(payload(false).replace(id.toString(),UUID.randomUUID().toString()),payload(false).replace("\"revision\":2","\"revision\":\"2\""),payload(false).replace("\"revision\":2","\"revision\":-1"),payload(false).replace("true","\"true\""),payload(false).replace("\"revision\":2","\"revision\":2,\"extra\":0"),payload(false).replace("\"revision\":2","\"revision\":2,\"revision\":3"),payload(false).replace(now.toString(),now.minusSeconds(6).toString()),payload(false).replace(now.toString(),now.plusSeconds(6).toString()),payload(false)+"{}")) {
   body=bad;assertEquals("ALERT_DEPENDENCY_INVALID",assertThrows(BusinessException.class,()->client.context(id)).code());
  }
  body=payload(true).replace("\"revision\":2","\"revision\":3");assertThrows(BusinessException.class,()->client.target(id,2));
 }
 @Test void explicitIdentityErrorsAreTerminalAndDoNotLeakResponseBody() {
  for(int status:new int[]{404,409}) {code=status;body="private recipient and sql";assertEquals("ALERT_RECIPIENT_CHANGED",assertThrows(BusinessException.class,()->client.target(id,2)).code());}
  code=503;assertEquals("ALERT_DEPENDENCY_UNAVAILABLE",assertThrows(BusinessException.class,()->client.target(id,2)).code());
 }
 @Test void rejectsRedirectAndOversizedBodyAndHeaderInjection() {
  code=302;assertThrows(BusinessException.class,()->client.context(id));code=200;body="x".repeat(65537);assertThrows(BusinessException.class,()->client.context(id));
  body=payload(true).replace("fixture@example.com","fixture@example.com\\r\\nBcc:attacker@example.com");assertEquals("ALERT_DEPENDENCY_INVALID",assertThrows(BusinessException.class,()->client.target(id,2)).code());
  assertThrows(BusinessException.class,()->client.target(id,-1));
 }
 @Test void unverifiedTargetMustNotExposeARecipient() {
  body=payload(true).replace("true","false").replace("\"fixture@example.com\"","null");assertNull(client.target(id,2).email());
  body=payload(true).replace("true","false");assertThrows(BusinessException.class,()->client.target(id,2));
 }
 @Test void deadlineIncludesBodyAfterHeaders()throws Exception {
  server.stop(0);server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(r->{var t=new Thread(r);t.setDaemon(true);return t;}));
  server.createContext("/",x->{try{x.sendResponseHeaders(200,0);x.getResponseBody().write('{');x.getResponseBody().flush();Thread.sleep(15000);}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{x.close();}});server.start();
  var stalled=new AlertMailAccountClient("http://127.0.0.1:"+server.getAddress().getPort(),"fixture",Clock.fixed(now,ZoneOffset.UTC));
  assertTimeoutPreemptively(Duration.ofSeconds(12),()->assertThrows(BusinessException.class,()->stalled.context(id)));
 }
}
