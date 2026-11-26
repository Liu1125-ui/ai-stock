package com.aistock.gateway;

import com.sun.net.httpserver.HttpServer;
import feign.Request;
import feign.RequestTemplate;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GatewayHttpTransportTest {
    @Test void upstreamCookiesNeverAuthenticateAnUnrelatedBrowser() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            String cookie=exchange.getRequestHeaders().getFirst("Cookie");byte[] response=(cookie==null?"ANONYMOUS":cookie).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Set-Cookie","AISTOCK_SESSION=synthetic-server-session; Path=/; HttpOnly");
            exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
        });server.start();
        try(var http=new GatewayHttpConfiguration().userApacheClient()){
            var client=new feign.hc5.ApacheHttp5Client(http);String url="http://127.0.0.1:"+server.getAddress().getPort()+"/";
            var first=Request.create(Request.HttpMethod.GET,url,Map.of("Cookie",List.of("AISTOCK_SESSION=browser-one")),null,StandardCharsets.UTF_8,new RequestTemplate());
            try(var result=client.execute(first,new Request.Options())){assertEquals("AISTOCK_SESSION=browser-one",new String(result.body().asInputStream().readAllBytes(),StandardCharsets.UTF_8));}
            var anonymous=Request.create(Request.HttpMethod.GET,url,Map.of(),null,StandardCharsets.UTF_8,new RequestTemplate());
            try(var result=client.execute(anonymous,new Request.Options())){assertEquals("ANONYMOUS",new String(result.body().asInputStream().readAllBytes(),StandardCharsets.UTF_8));}
            var second=Request.create(Request.HttpMethod.GET,url,Map.of("Cookie",List.of("AISTOCK_SESSION=browser-two")),null,StandardCharsets.UTF_8,new RequestTemplate());
            try(var result=client.execute(second,new Request.Options())){assertEquals("AISTOCK_SESSION=browser-two",new String(result.body().asInputStream().readAllBytes(),StandardCharsets.UTF_8));}
        }finally{server.stop(0);}
    }
    @Test void configuredTransportSendsPatchBodyToActualHttpServer() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/users/me",exchange->{
            String received=exchange.getRequestMethod()+":"+new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            byte[] response=received.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Set-Cookie","first=one; Path=/");
            exchange.getResponseHeaders().add("Set-Cookie","second=two; Path=/");
            exchange.sendResponseHeaders(200,response.length);
            exchange.getResponseBody().write(response);exchange.close();
        });
        server.start();
        try {
            var request=Request.create(Request.HttpMethod.PATCH,"http://127.0.0.1:"+server.getAddress().getPort()+"/api/v1/users/me",
                    Map.of("Content-Type",List.of("application/json")),"{\"nickname\":\"member\"}".getBytes(StandardCharsets.UTF_8),StandardCharsets.UTF_8,new RequestTemplate());
            try(var response=new GatewayHttpConfiguration().userHttpClient().execute(request,new Request.Options())) {
                assertEquals(200,response.status());
                assertEquals("PATCH:{\"nickname\":\"member\"}",new String(response.body().asInputStream().readAllBytes(),StandardCharsets.UTF_8));
                assertEquals(2,response.headers().entrySet().stream().filter(e->e.getKey().equalsIgnoreCase("Set-Cookie")).findFirst().orElseThrow().getValue().size());
            }
        } finally {server.stop(0);}
    }
}
