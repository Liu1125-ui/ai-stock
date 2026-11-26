package com.aistock.gateway;
/** Feign child context only, never a global component. */
public class PortfolioClientConfiguration {
 @org.springframework.context.annotation.Bean public feign.Request.Options portfolioOptions(){return new feign.Request.Options(2000,java.util.concurrent.TimeUnit.MILLISECONDS,50000,java.util.concurrent.TimeUnit.MILLISECONDS,false);}
 @org.springframework.context.annotation.Bean public feign.Retryer portfolioRetryer(){return feign.Retryer.NEVER_RETRY;}
}
