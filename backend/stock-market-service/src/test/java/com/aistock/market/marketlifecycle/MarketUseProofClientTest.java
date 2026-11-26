package com.aistock.market.marketlifecycle;

import com.aistock.common.marketlifecycle.MarketUseRecords.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class MarketUseProofClientTest {
    private HttpServer server;private ExecutorService executor;private String base;
    private final AtomicReference<String> content=new AtomicReference<>();private final AtomicInteger status=new AtomicInteger(200),hits=new AtomicInteger();
    private final AtomicLong delay=new AtomicLong();private final AtomicReference<String> seenToken=new AtomicReference<>(),seenPath=new AtomicReference<>();
    private final String id="00000000-0000-0000-0000-000000000001",version="00000000-0000-0000-0000-000000000002";
    private MarketLifecycleRecords.Bundle bundle(){return new MarketLifecycleRecords.Bundle(id,new UseIdentity("prediction","FORECAST",id,"1"),"ACTIVE",List.of(version),List.of());}
    private String valid(){return "{\"code\":\"OK\",\"data\":{\"owner\":\"prediction\",\"purpose\":\"FORECAST\",\"ownerOperationId\":\""+id+"\",\"generation\":\"1\",\"state\":\"SUCCEEDED\",\"terminalAt\":\"2026-11-04T00:00:00Z\",\"fencingRevision\":\"1\",\"resultIdentity\":\""+id+"\",\"versionIds\":[\""+version+"\"]}}";}
    @BeforeEach void start()throws Exception {
        content.set(valid());server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);executor=Executors.newCachedThreadPool();server.setExecutor(executor);
        server.createContext("/",exchange->{try {
            hits.incrementAndGet();seenToken.set(exchange.getRequestHeaders().getFirst("X-Service-Token"));seenPath.set(exchange.getRequestURI().toString());
            var bytes=content.get().getBytes(StandardCharsets.UTF_8);if(status.get()==302)exchange.getResponseHeaders().set("Location",base+"/redirected");
            exchange.sendResponseHeaders(status.get(),bytes.length);if(delay.get()>0)try{Thread.sleep(delay.get());}catch(InterruptedException e){Thread.currentThread().interrupt();}
            exchange.getResponseBody().write(bytes);
        }finally{exchange.close();}});server.start();base="http://127.0.0.1:"+server.getAddress().getPort();
    }
    @AfterEach void stop(){server.stop(0);executor.shutdownNow();}
    private MarketUseProofClient client(Duration timeout){return new MarketUseProofClient(Map.of("prediction",base,"notification",base,"market",base),"private",timeout);}
    @Test void readsOnlyFixedOwnerPathAndMatchesFullIdentity() {
        assertTrue(client(Duration.ofSeconds(2)).read(bundle()).isPresent());assertEquals("private",seenToken.get());
        assertEquals("/internal/v1/market-version-use-proofs/FORECAST/"+id+"?generation=1",seenPath.get());
    }
    @Test void unknownWrongGenerationOrVersionNeverBecomeProof() {
        var client=client(Duration.ofSeconds(2));
        for(var bad:List.of(valid().replace("SUCCEEDED","UNKNOWN"),valid().replace("\"generation\":\"1\"","\"generation\":\"2\""),valid().replace(version,id),valid().replace("\"generation\":\"1\"","\"generation\":1"),valid().replace("\"fencingRevision\":\"1\"","\"fencingRevision\":\"0\""))) {
            content.set(bad);assertTrue(client.read(bundle()).isEmpty());
        }
    }
    @Test void rejectsDuplicateJsonAndOversizedBody() {
        var client=client(Duration.ofSeconds(2));content.set(valid().replace("\"state\":","\"state\":\"FAILED\",\"state\":"));assertTrue(client.read(bundle()).isEmpty());
        content.set(" ".repeat(65537));assertTrue(client.read(bundle()).isEmpty());
    }
    @Test void redirectsAreNotFollowed() {
        status.set(302);assertTrue(client(Duration.ofSeconds(2)).read(bundle()).isEmpty());assertEquals(1,hits.get());
    }
    @Test void stalledBodyIsBoundedAndDoesNotInventSuccess() {
        delay.set(800);var timer=System.nanoTime();assertTrue(client(Duration.ofMillis(100)).read(bundle()).isEmpty());assertTrue(Duration.ofNanos(System.nanoTime()-timer).compareTo(Duration.ofSeconds(3))<0);
    }
    @Test void missingTokenAndInvalidConfiguredOwnerUrlCannotSend() {
        assertTrue(new MarketUseProofClient(Map.of("prediction",base,"notification",base,"market",base),"",Duration.ofSeconds(1)).read(bundle()).isEmpty());assertEquals(0,hits.get());
        assertThrows(IllegalArgumentException.class,()->new MarketUseProofClient(Map.of("prediction",base+"/custom","notification",base,"market",base),"private",Duration.ofSeconds(1)));
    }
}
