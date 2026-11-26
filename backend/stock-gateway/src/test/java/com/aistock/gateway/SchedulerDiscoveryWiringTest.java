package com.aistock.gateway;
import com.sun.net.httpserver.HttpServer;
import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.test.context.*;import org.springframework.test.annotation.DirtiesContext;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.cloud.nacos.discovery.enabled=false","aistock.scheduler.url=","spring.cloud.loadbalancer.retry.enabled=false"})
@DirtiesContext
class SchedulerDiscoveryWiringTest {
 static final AtomicInteger calls=new AtomicInteger();static final HttpServer SERVER=start();
 static HttpServer start(){try{var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/api/v1/",x->{calls.incrementAndGet();x.getRequestBody().readAllBytes();var body="{}".getBytes(StandardCharsets.UTF_8);x.sendResponseHeaders(200,body.length);x.getResponseBody().write(body);x.close();});server.start();return server;}catch(java.io.IOException e){throw new IllegalStateException(e);}}
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.cloud.discovery.client.simple.instances.stock-scheduler-service[0].uri",()->"http://127.0.0.1:"+SERVER.getAddress().getPort());}
 @Autowired SchedulerPublicClient client;
 @AfterAll static void stop(){SERVER.stop(0);}
 @Test void schedulerWithoutExplicitUrlResolvesDiscoveredNonDefaultPort(){
  var response=client.post("admin/tasks/00000000-0000-0000-0000-000000000001/cancel","{}".getBytes(StandardCharsets.UTF_8),Map.of("Content-Type","application/json"));
  assertEquals(200,response.getStatusCode().value());assertEquals(1,calls.get());
 }
}
