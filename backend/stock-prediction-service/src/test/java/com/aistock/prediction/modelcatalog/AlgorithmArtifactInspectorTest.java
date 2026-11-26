package com.aistock.prediction.modelcatalog;
import com.aistock.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AlgorithmArtifactInspectorTest {
    @Test void stalledBodyAfterHeadersReturnsWithinTotalDeadline()throws Exception{
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var executor=Executors.newCachedThreadPool();var release=new CountDownLatch(1);
        server.setExecutor(executor);server.createContext("/internal/v1/model-artifacts/inspect",exchange->{
            exchange.getRequestBody().readAllBytes();exchange.sendResponseHeaders(200,100);
            try(var output=exchange.getResponseBody()){
                output.write('{');output.flush();
                try{release.await(25,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            }
        });server.start();
        try{
            var inspector=new AlgorithmArtifactInspector("http://127.0.0.1:"+server.getAddress().getPort(),"isolated-test-token",new ObjectMapper());
            assertTimeoutPreemptively(Duration.ofSeconds(17),()->{
                var error=assertThrows(BusinessException.class,()->inspector.inspect("transformer-s3-2023-seed42","a".repeat(64)));
                assertEquals("MODEL_ARTIFACT_UNAVAILABLE",error.code());assertEquals(503,error.status().value());
            });
        }finally{release.countDown();server.stop(0);executor.shutdownNow();}
    }
    @Test void oversizedResponseStillHasByteLimit()throws Exception{
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/v1/model-artifacts/inspect",exchange->{exchange.getRequestBody().readAllBytes();exchange.sendResponseHeaders(200,131073);try(var output=exchange.getResponseBody()){output.write(new byte[131073]);}});server.start();
        try{
            var inspector=new AlgorithmArtifactInspector("http://127.0.0.1:"+server.getAddress().getPort(),"isolated-test-token",new ObjectMapper());
            var error=assertThrows(BusinessException.class,()->inspector.inspect("transformer-s3-2023-seed42","a".repeat(64)));
            assertEquals("MODEL_ARTIFACT_UNAVAILABLE",error.code());
        }finally{server.stop(0);}
    }
}
