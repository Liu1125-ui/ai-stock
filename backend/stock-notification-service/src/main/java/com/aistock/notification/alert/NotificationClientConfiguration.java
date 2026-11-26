package com.aistock.notification.alert;
// Feign child-context configuration, deliberately not component-scanned as global defaults.
public class NotificationClientConfiguration {
 @org.springframework.context.annotation.Bean public feign.Capability alertResponseLimit(){return new NotificationResponseLimit();}
 @org.springframework.context.annotation.Bean public feign.Request.Options alertClientOptions(){return new feign.Request.Options(2,java.util.concurrent.TimeUnit.SECONDS,5,java.util.concurrent.TimeUnit.SECONDS,false);}
 @org.springframework.context.annotation.Bean public feign.Retryer alertRetryer(){return feign.Retryer.NEVER_RETRY;}
}
