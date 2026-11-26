package com.aistock.prediction.statistics;
import com.aistock.common.*;
import com.aistock.prediction.security.SessionIdentityClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class StatisticsIdentityJsonTest {
 private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
 private String json(String field){return "{\"userId\":\"00000000-0000-0000-0000-000000000001\",\"role\":\"ADMIN\",\"experimentAllowed\":false"+field+"}";}
 @Test void malformedJsonIdentityFailsClosed()throws Exception{for(var field:new String[]{"",",\"credentialVersion\":null",",\"credentialVersion\":1.5",",\"credentialVersion\":\"2\"",",\"credentialVersion\":true",",\"credentialVersion\":-1",",\"credentialVersion\":9223372036854775808"})check(json(field),false);}
 @Test void canonicalIntegerZeroAndLongMaxRemainValid()throws Exception{for(var field:new String[]{",\"credentialVersion\":0",",\"credentialVersion\":1",",\"credentialVersion\":9223372036854775807"})check(json(field),true);}
 private void check(String json,boolean valid)throws Exception{var client=mock(SessionIdentityClient.class);when(client.resolve(any(),anyString())).thenAnswer(x->ApiResponse.success(mapper.convertValue(mapper.readTree(json),SessionIdentityClient.Identity.class),"test"));var request=new MockHttpServletRequest("GET","/api/v1/admin/statistics/predictions");request.setCookies(new Cookie("AISTOCK_SESSION","test"));var response=new MockHttpServletResponse();var passed=new AtomicBoolean();try{new com.aistock.prediction.security.PredictionSessionFilter(client,"server-test",new com.aistock.prediction.security.SecurityErrorWriter(mapper)).doFilter(request,response,(a,b)->passed.set(true));assertEquals(valid,passed.get());assertEquals(valid?200:503,response.getStatus());if(!valid)assertTrue(response.getContentAsString().contains("USER_SERVICE_UNAVAILABLE"));}finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}}
}
