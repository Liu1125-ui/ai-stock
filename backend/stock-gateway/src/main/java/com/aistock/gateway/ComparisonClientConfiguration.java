package com.aistock.gateway;
/** Feign child context only, never a global component. */
public class ComparisonClientConfiguration {
 @org.springframework.context.annotation.Bean public feign.Request.Options comparisonOptions(){return new feign.Request.Options(2000,java.util.concurrent.TimeUnit.MILLISECONDS,30000,java.util.concurrent.TimeUnit.MILLISECONDS,false);}
 @org.springframework.context.annotation.Bean public feign.Retryer comparisonRetryer(){return feign.Retryer.NEVER_RETRY;}
}
