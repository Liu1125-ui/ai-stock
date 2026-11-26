package com.aistock.gateway;
import com.aistock.common.ApiExceptionHandler;import com.fasterxml.jackson.databind.ObjectMapper;import java.util.*;import org.junit.jupiter.api.Test;import org.springframework.http.ResponseEntity;import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class AlertMailProxyTest {
 @Test void onlyExactReadOnlyOwnerEndpointIsProxied()throws Exception{
  var client=mock(NotificationPublicClient.class);when(client.get(anyString(),anyMap(),anyMap())).thenReturn(ResponseEntity.ok("{}".getBytes()));var mvc=MockMvcBuilders.standaloneSetup(new NotificationApiProxyController(client,new ObjectMapper())).setControllerAdvice(new ApiExceptionHandler()).build();String id=UUID.randomUUID().toString();String path="/api/v1/messages/"+id+"/email-delivery";
  mvc.perform(get(path)).andExpect(status().isOk());verify(client).get(eq("messages/"+id+"/email-delivery"),eq(Map.of()),anyMap());mvc.perform(post(path)).andExpect(status().isNotFound());mvc.perform(get(path+"?ownerId=forged")).andExpect(status().isBadRequest());mvc.perform(get("/internal/v1/users/"+id+"/alert-mail-target?revision=0")).andExpect(status().isNotFound());
 }
}
