package com.aistock.gateway;

import feign.Client;
import feign.hc5.ApacheHttp5Client;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Apache HTTP transport supports PATCH and preserves multiple response headers. */
@Configuration
public class GatewayHttpConfiguration {
    @Bean(destroyMethod="close")
    public org.apache.hc.client5.http.impl.classic.CloseableHttpClient userApacheClient(){
        // A proxy must forward only the current browser's Cookie, never keep a shared cookie jar.
        return org.apache.hc.client5.http.impl.classic.HttpClients.custom().useSystemProperties().disableCookieManagement().build();
    }
    // Client is supplied by Spring Cloud's HC5 load-balancer configuration, which wraps this transport.
    public Client userHttpClient(){return new ApacheHttp5Client(userApacheClient());}
}
