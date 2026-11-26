package com.aistock.market;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import com.aistock.common.ApiExceptionHandler;

@org.springframework.cloud.openfeign.EnableFeignClients
@SpringBootApplication
@Import(ApiExceptionHandler.class)
public class MarketApplication {
    public static void main(String[] args) { SpringApplication.run(MarketApplication.class, args); }
}
