package com.aistock.gateway;
import com.sun.net.httpserver.HttpServer;
import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.test.annotation.DirtiesContext;
import org.junit.jupiter.api.*;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.test.context.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.cloud.nacos.discovery.enabled=false"})
@DirtiesContext
class SchedulerFeignWiringTest {
 static final AtomicInteger calls=new AtomicInteger();
 static final HttpServer SERVER=start();
 static HttpServer start(){try{var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/api/v1/",x->{calls.incrementAndGet();x.getRequestBody().readAllBytes();var body="{\"code\":\"TASK_ACTION_UNAVAILABLE\",\"data\":null}".getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(503,body.length);x.getResponseBody().write(body);x.close();});server.start();return server;}catch(java.io.IOException e){throw new IllegalStateException(e);}}
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("aistock.scheduler.url",()->"http://127.0.0.1:"+SERVER.getAddress().getPort());}
 @Autowired SchedulerPublicClient client;
 @AfterAll static void stop(){SERVER.stop(0);}
 @Test void actualFeignChildContextMakesOnlyOneUnavailablePost(){
  assertThrows(feign.FeignException.class,()->client.post("admin/tasks/00000000-0000-0000-0000-000000000001/cancel","{}".getBytes(StandardCharsets.UTF_8),Map.of("Content-Type","application/json")));
  assertEquals(1,calls.get());
 }
}
