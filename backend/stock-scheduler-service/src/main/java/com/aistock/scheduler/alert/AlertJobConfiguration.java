package com.aistock.scheduler.alert;
import org.springframework.context.annotation.*;import org.springframework.beans.factory.annotation.Value;import com.fasterxml.jackson.databind.ObjectMapper;
@Configuration public class AlertJobConfiguration {
 @Bean AlertTaskClient alertTaskClient(@Value("${aistock.alert.market-url:${aistock.revision.market-url:http://127.0.0.1:18082}}")String market,@Value("${aistock.alert.notification-url:http://127.0.0.1:18085}")String notification,@Value("${aistock.internal.token:}")String token,ObjectMapper mapper){return new HttpAlertTaskClient(market,notification,token,mapper);}
 @Bean AlertMarketClient alertMarketClient(@Value("${aistock.alert.market-url:${aistock.revision.market-url:http://127.0.0.1:18082}}")String market,@Value("${aistock.alert.notification-url:http://127.0.0.1:18085}")String notification,@Value("${aistock.internal.token:}")String token,ObjectMapper mapper,java.time.Clock clock){return new HttpAlertMarketClient(market,notification,token,mapper,clock);}
}
