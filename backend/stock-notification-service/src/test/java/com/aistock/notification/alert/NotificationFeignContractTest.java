package com.aistock.notification.alert;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;import java.nio.charset.StandardCharsets;import java.util.UUID;
import org.junit.jupiter.api.Test;import org.springframework.boot.autoconfigure.EnableAutoConfiguration;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.context.annotation.Configuration;import org.springframework.cloud.openfeign.EnableFeignClients;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.test.context.DynamicPropertyRegistry;import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(classes=NotificationFeignContractTest.Config.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={"spring.cloud.nacos.discovery.enabled=false","spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"})
class NotificationFeignContractTest {
 @Configuration @EnableAutoConfiguration @EnableFeignClients(clients={NotificationIdentityClient.class,AlertBoundaryClient.class}) static class Config{}
 static final UUID OWNER=UUID.fromString("11111111-1111-4111-8111-111111111111");static final HttpServer SERVER;
 static {try{SERVER=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
 SERVER.createContext("/internal/v1/sessions/resolve",e->{String json="{\"code\":\"OK\",\"message\":\"ok\",\"traceId\":\"trace\",\"data\":{\"userId\":\""+OWNER+"\",\"role\":\"ADMIN\",\"experimentAllowed\":true,\"credentialVersion\":0}}";send(e,json);});
 SERVER.createContext("/api/v1/users/me",e->send(e,"{\"code\":\"OK\",\"message\":\"ok\",\"traceId\":\"trace\",\"data\":{\"userId\":\""+OWNER+"\",\"email\":\"fixture@example.test\",\"nickname\":\"fixture\",\"role\":\"ADMIN\",\"status\":\"ENABLED\",\"emailVerified\":true,\"experimentAllowed\":true,\"revision\":0}}"));SERVER.start();}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 static void send(com.sun.net.httpserver.HttpExchange e,String json)throws java.io.IOException{e.getRequestBody().readAllBytes();byte[] bytes=json.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();}
 @DynamicPropertySource static void urls(DynamicPropertyRegistry r){r.add("aistock.user.url",()->"http://127.0.0.1:"+SERVER.getAddress().getPort());r.add("aistock.market.url",()->"http://127.0.0.1:"+SERVER.getAddress().getPort());}
 @Autowired NotificationIdentityClient client;
 @Test void realFeignDecodesSessionAndFullProfileContract(){var access=new NotificationAccess(client,"fixture-service-token");assertEquals(OWNER,access.resolve("fixture-session"));assertTrue(access.emailVerified(OWNER,"fixture-session"));}
 @org.junit.jupiter.api.AfterAll static void stop(){SERVER.stop(0);}
}
