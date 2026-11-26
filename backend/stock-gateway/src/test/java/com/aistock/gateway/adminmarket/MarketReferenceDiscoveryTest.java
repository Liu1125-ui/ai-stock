package com.aistock.gateway.adminmarket;
import com.aistock.gateway.MarketReferencePublicClient;import com.sun.net.httpserver.HttpServer;import java.net.*;import java.util.*;import java.util.concurrent.atomic.*;import org.junit.jupiter.api.*;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.test.context.*;import org.springframework.test.annotation.DirtiesContext;import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.cloud.nacos.discovery.enabled=false","aistock.market.url=","SERVICE_TOKEN=test-only"}) @DirtiesContext
class MarketReferenceDiscoveryTest {
 static final AtomicInteger calls=new AtomicInteger(),status=new AtomicInteger(200);static final AtomicReference<byte[]> body=new AtomicReference<>("{}".getBytes());static final AtomicInteger delay=new AtomicInteger();static final HttpServer SERVER=start();
 static HttpServer start(){try{var s=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);s.createContext("/",e->{calls.incrementAndGet();e.getResponseHeaders().set("Content-Type","application/json");e.getResponseHeaders().set("Location","/redirected");try{if(delay.get()>0)Thread.sleep(delay.get());var b=body.get();e.sendResponseHeaders(status.get(),b.length);e.getResponseBody().write(b);}catch(Exception ignored){}finally{e.close();}});s.start();return s;}catch(java.io.IOException e){throw new IllegalStateException(e);}}
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.cloud.discovery.client.simple.instances.stock-market-service[0].uri",()->"http://127.0.0.1:"+SERVER.getAddress().getPort());}
 @Autowired MarketReferencePublicClient client;
 @BeforeEach void reset(){calls.set(0);status.set(200);body.set("{}".getBytes());delay.set(0);}
 @AfterAll static void stop(){SERVER.stop(0);}
 @Test void dedicatedDiscoveredClientSurvivesBeyondOldFiveSecondReadTimeout(){delay.set(5500);assertEquals(200,client.inspect("SH600030","11111111-1111-1111-1111-111111111111",Map.of()).getStatusCode().value());assertEquals(1,calls.get());}
 @Test void boundedDecodeAndRedirectNeverRetryOrFollow(){body.set(new byte[65537]);assertThrows(RuntimeException.class,()->client.inspect("SH600030","11111111-1111-1111-1111-111111111111",Map.of()));assertEquals(1,calls.get());body.set("{}".getBytes());status.set(302);assertThrows(RuntimeException.class,()->client.inspect("SH600030","11111111-1111-1111-1111-111111111111",Map.of()));assertEquals(2,calls.get());}
}
