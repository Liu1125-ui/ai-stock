package com.aistock.gateway;
import feign.Client;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.loadbalancer.FeignBlockingLoadBalancerClient;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.cloud.nacos.discovery.enabled=false"})
class GatewayDiscoveryTransportTest {
    @Autowired Client client;
    @Test void configuredTransportKeepsNacosLoadBalancing(){assertInstanceOf(FeignBlockingLoadBalancerClient.class,client);}
}
