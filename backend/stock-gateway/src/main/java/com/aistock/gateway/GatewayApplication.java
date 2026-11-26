package com.aistock.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.cloud.openfeign.EnableFeignClients
@org.springframework.context.annotation.Import(com.aistock.common.ApiExceptionHandler.class)
public class GatewayApplication {
    public static void main(String[] args) { SpringApplication.run(GatewayApplication.class, args); }
}
