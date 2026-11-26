package com.aistock.gateway;
/** Feign child-context configuration only: keep other clients' short timeouts. */
public class ForecastClientConfiguration {
 @org.springframework.context.annotation.Bean feign.Request.Options forecastOptions(){return new feign.Request.Options(2000,java.util.concurrent.TimeUnit.MILLISECONDS,185000,java.util.concurrent.TimeUnit.MILLISECONDS,false);}
}
