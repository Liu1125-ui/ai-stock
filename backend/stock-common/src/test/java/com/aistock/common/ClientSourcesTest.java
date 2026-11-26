package com.aistock.common;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
class ClientSourcesTest {
 @Test void onlyKnownProxyAndAuthenticatedGatewayCanSupplyClientAddress(){
  var request=new MockHttpServletRequest();request.setRemoteAddr("127.0.0.1");request.addHeader("X-Real-IP","203.0.113.10");
  assertEquals("203.0.113.10",ClientSources.gateway(request,"localhost"));
  request.setRemoteAddr("192.0.2.5");assertEquals("192.0.2.5",ClientSources.gateway(request,"localhost"));
  request.addHeader("X-Auth-Source","203.0.113.20");request.addHeader("X-Service-Token","forged");
  assertEquals("192.0.2.5",ClientSources.user(request,"private-test-service-token"));
  request.removeHeader("X-Service-Token");request.addHeader("X-Service-Token","private-test-service-token");
  assertEquals("203.0.113.20",ClientSources.user(request,"private-test-service-token"));
  request.removeHeader("X-Auth-Source");request.addHeader("X-Auth-Source","203.0.113.20, 192.0.2.1");
  assertEquals("192.0.2.5",ClientSources.user(request,"private-test-service-token"));
 }
}
