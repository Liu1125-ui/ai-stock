package com.aistock.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.cloud.openfeign.EnableFeignClients
@org.springframework.scheduling.annotation.EnableScheduling
@org.springframework.context.annotation.Import(com.aistock.common.ApiExceptionHandler.class)
@SpringBootApplication
public class SchedulerApplication {
    public static void main(String[] args) { SpringApplication.run(SchedulerApplication.class, args); }
}
