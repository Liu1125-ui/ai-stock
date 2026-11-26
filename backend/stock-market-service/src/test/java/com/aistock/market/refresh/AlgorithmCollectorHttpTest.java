package com.aistock.market.refresh;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AlgorithmCollectorHttpTest {
    private static final LocalDate DAY=LocalDate.of(2026,9,24);
    @Test void refusedConnectionIsRetryable()throws Exception {
        int port;try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}
        assertEquals("SOURCE_UNAVAILABLE",new AlgorithmCollectorClient("http://127.0.0.1:"+port,"token").collect("op","SH600030",DAY,DAY).state());
    }
    @Test void resetConnectionIsRetryable()throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",exchange->exchange.close());server.start();
        try{assertEquals("SOURCE_UNAVAILABLE",new AlgorithmCollectorClient("http://127.0.0.1:"+server.getAddress().getPort(),"token").collect("op","SH600030",DAY,DAY).state());}finally{server.stop(0);}
    }
    @Test void malformedJsonAndOversizeAreNotRetryable()throws Exception {
        for(var bytes:new byte[][]{"{".getBytes(StandardCharsets.UTF_8),new byte[2097153]}){
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/",exchange->{try{exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);}finally{exchange.close();}});server.start();
            try{assertEquals("SOURCE_INVALID",new AlgorithmCollectorClient("http://127.0.0.1:"+server.getAddress().getPort(),"token").collect("op","SH600030",DAY,DAY).state());}finally{server.stop(0);}
        }
    }
}
