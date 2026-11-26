package com.aistock.gateway;
/** Feign child context only, never a global component. */
public class PortfolioQuestionClientConfiguration {
 @org.springframework.context.annotation.Bean public feign.Request.Options portfolioQuestionOptions(){return new feign.Request.Options(2000,java.util.concurrent.TimeUnit.MILLISECONDS,65000,java.util.concurrent.TimeUnit.MILLISECONDS,false);}
 @org.springframework.context.annotation.Bean public feign.Retryer portfolioQuestionRetryer(){return feign.Retryer.NEVER_RETRY;}
}
