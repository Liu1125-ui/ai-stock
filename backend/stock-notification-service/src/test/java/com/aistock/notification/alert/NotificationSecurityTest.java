package com.aistock.notification.alert;
import com.aistock.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
class NotificationSecurityTest {
 NotificationAccess access; AlertRuleService rules; NotificationPreferenceService prefs; MockMvc mvc;
 UUID owner=UUID.randomUUID();
 @BeforeEach void setup(){access=mock(NotificationAccess.class);rules=mock(AlertRuleService.class);prefs=mock(NotificationPreferenceService.class);
 when(access.resolve("session")).thenReturn(owner);when(access.emailVerified(owner,"session")).thenReturn(true);
 when(rules.list(owner,1,20)).thenReturn(new AlertRecords.Page(java.util.List.of(),1,20,0));
 mvc=MockMvcBuilders.standaloneSetup(new AlertController(rules),new NotificationPreferenceController(prefs,access))
 .setControllerAdvice(new ApiExceptionHandler()).addFilters(new NotificationSecurityFilter(access,new ObjectMapper(),"http://localhost:15173,http://127.0.0.1:15173")).build();}
 @Test void anonymousAndForgedOwnerCannotRead()throws Exception{mvc.perform(get("/api/v1/alerts").header("X-User-Id",owner)).andExpect(status().isUnauthorized());verifyNoInteractions(rules);}
 @Test void realSessionDeterminesOwner()throws Exception{mvc.perform(get("/api/v1/alerts").cookie(new Cookie("AISTOCK_SESSION","session")).header("X-User-Id",UUID.randomUUID())).andExpect(status().isOk());verify(rules).list(owner,1,20);}
 @Test void writesNeedBothOriginAndCsrf()throws Exception{for(String origin:new String[]{"http://evil.example","http://localhost:15173"})mvc.perform(post("/api/v1/alerts").cookie(new Cookie("AISTOCK_SESSION","session")).header("Origin",origin).contentType("application/json").content("{}")).andExpect(status().isForbidden());verifyNoInteractions(rules);}
 @Test void unknownOwnerFieldRejectedBeforeService()throws Exception{mvc.perform(post("/api/v1/alerts").cookie(new Cookie("AISTOCK_SESSION","session"),new Cookie("XSRF-TOKEN","csrf")).header("Origin","http://localhost:15173").header("X-XSRF-TOKEN","csrf").contentType("application/json").content("{\"userId\":\"forged\"}")).andExpect(status().isBadRequest());verifyNoInteractions(rules);}
 @Test void expiredAndUnavailableAccountFailClosed()throws Exception{when(access.resolve("session")).thenThrow(new BusinessException("AUTH_REQUIRED","登录失效",org.springframework.http.HttpStatus.UNAUTHORIZED));mvc.perform(get("/api/v1/alerts").cookie(new Cookie("AISTOCK_SESSION","session"))).andExpect(status().isUnauthorized());doThrow(new BusinessException("ACCOUNT_SERVICE_UNAVAILABLE","账号暂不可用",org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)).when(access).resolve("session");mvc.perform(get("/api/v1/alerts").cookie(new Cookie("AISTOCK_SESSION","session"))).andExpect(status().isServiceUnavailable());}
 @Test void oversizedBodyIsRejected()throws Exception{mvc.perform(post("/api/v1/alerts").cookie(new Cookie("AISTOCK_SESSION","session"),new Cookie("XSRF-TOKEN","csrf")).header("Origin","http://localhost:15173").header("X-XSRF-TOKEN","csrf").contentType("application/json").content("x".repeat(5000))).andExpect(status().isPayloadTooLarge());verifyNoInteractions(rules);}
}
