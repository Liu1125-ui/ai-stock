package com.aistock.notification.alert;
import com.fasterxml.jackson.databind.ObjectMapper;import java.time.*;import org.springframework.context.annotation.*;import org.springframework.beans.factory.annotation.Value;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.scheduling.annotation.*;
@Configuration(proxyBeanMethods=false) public class AlertMarketUseConfiguration {
 @Bean AlertMarketUseClient alertMarketUseClient(@Value("${aistock.market-use.market-url:http://127.0.0.1:18082}")String base,@Value("${aistock.internal.token:}")String token){return new AlertMarketUseClient(base,token,Duration.ofSeconds(4));}
 @Bean AlertMarketUseOwner alertMarketUseOwner(AlertRuleRepository repo,ObjectMapper mapper,AlertMarketUseClient client,Clock clock,@Value("${aistock.market-use.alert-enabled:false}")boolean enabled){return new AlertMarketUseOwner(repo,mapper,client,clock,enabled);}
 @Bean Recovery alertMarketUseRecovery(AlertMarketUseOwner uses,@Value("${aistock.market-use.recovery-enabled:false}")boolean enabled){return new Recovery(uses,enabled);}
 public static final class Recovery {private final AlertMarketUseOwner uses;private final boolean enabled;Recovery(AlertMarketUseOwner uses,boolean enabled){this.uses=uses;this.enabled=enabled;}@Scheduled(fixedDelayString="${aistock.market-use.recovery-delay-ms:60000}",initialDelayString="${aistock.market-use.recovery-delay-ms:60000}")public void recover(){if(enabled)uses.recoverBatch();}}
 @Configuration(proxyBeanMethods=false) @ConditionalOnProperty(name="aistock.market-use.recovery-enabled",havingValue="true") @EnableScheduling static class Scheduling{}
}
