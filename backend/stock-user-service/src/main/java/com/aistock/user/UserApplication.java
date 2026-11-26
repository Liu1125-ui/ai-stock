package com.aistock.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.context.annotation.Import(com.aistock.common.ApiExceptionHandler.class)
@org.springframework.cloud.openfeign.EnableFeignClients
@org.springframework.scheduling.annotation.EnableScheduling
public class UserApplication {
    public static void main(String[] args) {
        if("1".equals(System.getenv("AISTOCK_ADMIN_BOOTSTRAP"))){System.exit(com.aistock.user.admin.AdminBootstrapApplication.execute());return;}
        SpringApplication.run(UserApplication.class, args);
    }
}
