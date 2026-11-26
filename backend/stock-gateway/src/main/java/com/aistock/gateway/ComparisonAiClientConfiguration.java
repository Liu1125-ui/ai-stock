package com.aistock.gateway;
/** Feign child context only, never a global component. */
public class ComparisonAiClientConfiguration {
 @org.springframework.context.annotation.Bean public feign.Request.Options comparisonAiOptions(){return new feign.Request.Options(2000,java.util.concurrent.TimeUnit.MILLISECONDS,65000,java.util.concurrent.TimeUnit.MILLISECONDS,false);}
 @org.springframework.context.annotation.Bean public feign.Retryer comparisonAiRetryer(){return feign.Retryer.NEVER_RETRY;}
}
