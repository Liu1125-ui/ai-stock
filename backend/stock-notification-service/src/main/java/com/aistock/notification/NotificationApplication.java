package com.aistock.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.cloud.openfeign.EnableFeignClients
@SpringBootApplication
@org.springframework.context.annotation.Import(com.aistock.common.ApiExceptionHandler.class)
public class NotificationApplication {
    public static void main(String[] args) { SpringApplication.run(NotificationApplication.class, args); }
}
