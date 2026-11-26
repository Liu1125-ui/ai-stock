package com.aistock.gateway.statistics;
import com.aistock.common.*;

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
 private void check(String json,boolean valid)throws Exception{var client=mock(StatisticsIdentityClient.class);when(client.resolve(any(),anyString())).thenAnswer(x->ApiResponse.success(mapper.convertValue(mapper.readTree(json),StatisticsIdentityClient.Identity.class),"test"));var request=new MockHttpServletRequest("GET","/api/v1/admin/statistics/http");request.setCookies(new Cookie("AISTOCK_SESSION","test"));var access=new StatisticsAdminAccess(client,"server-test");if(valid)assertDoesNotThrow(()->access.requireAdmin(request));else{var e=assertThrows(BusinessException.class,()->access.requireAdmin(request));assertEquals(503,e.status().value());assertEquals("USER_SERVICE_UNAVAILABLE",e.code());}}
}
