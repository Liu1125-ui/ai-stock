package com.aistock.gateway;

import feign.*;
import feign.codec.*;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.http.*;

/** Loaded only inside the scheduler Feign client context, never component-scanned globally. */
public class SchedulerPublicFeignConfiguration {
 private static final int MAX_BYTES=262144;
 @Bean(destroyMethod="close") public org.apache.hc.client5.http.impl.classic.CloseableHttpClient schedulerApacheClient() {
  return org.apache.hc.client5.http.impl.classic.HttpClients.custom().disableAutomaticRetries().disableRedirectHandling().disableCookieManagement().build();
 }
 @Bean public Client schedulerTransport(@Qualifier("schedulerApacheClient")org.apache.hc.client5.http.impl.classic.CloseableHttpClient client,
   org.springframework.cloud.client.loadbalancer.LoadBalancerClient loadBalancer,
   org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory factory,
   org.springframework.beans.factory.ObjectProvider<org.springframework.cloud.openfeign.loadbalancer.LoadBalancerFeignRequestTransformer> transformers){
  return new org.springframework.cloud.openfeign.loadbalancer.FeignBlockingLoadBalancerClient(
    new feign.hc5.ApacheHttp5Client(client),loadBalancer,factory,transformers.orderedStream().toList());
 }
 @Bean public Retryer schedulerRetryer(){return Retryer.NEVER_RETRY;}
 @Bean public Decoder schedulerDecoder(){return (response,type)->{
  var headers=new HttpHeaders();response.headers().forEach((k,v)->headers.put(k,java.util.List.copyOf(v)));
  return ResponseEntity.status(response.status()).headers(headers).body(bounded(response));
 };}
 @Bean public ErrorDecoder schedulerErrorDecoder(){return (key,response)->{
  try{return FeignException.errorStatus(key,response.toBuilder().body(bounded(response)).build());}
  catch(IOException e){return new com.aistock.common.BusinessException("SCHEDULER_SERVICE_UNAVAILABLE","任务服务响应异常，请查询原操作结果",HttpStatus.SERVICE_UNAVAILABLE);}
 };}
 private byte[] bounded(Response response)throws IOException {
  if(response.body()==null)return new byte[0];
  try(var stream=response.body().asInputStream()) {
   var bytes=stream.readNBytes(MAX_BYTES+1);if(bytes.length>MAX_BYTES)throw new IOException("Scheduler response limit exceeded");return bytes;
  }
 }
}
