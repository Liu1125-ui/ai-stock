package com.aistock.prediction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import com.aistock.common.ApiExceptionHandler;

@SpringBootApplication
@EnableFeignClients
@Import(ApiExceptionHandler.class)
public class PredictionApplication {
    public static void main(String[] args) { SpringApplication.run(PredictionApplication.class, args); }
}
