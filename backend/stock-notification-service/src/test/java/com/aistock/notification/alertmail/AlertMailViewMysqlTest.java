package com.aistock.notification.alertmail;
import com.aistock.common.*;import com.aistock.notification.alert.*;import com.fasterxml.jackson.databind.ObjectMapper;import jakarta.servlet.http.Cookie;
import java.util.*;import org.junit.jupiter.api.*;import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@Tag("mysql") class AlertMailViewMysqlTest extends AlertMailDatabaseFixture {
 UUID message(){return jdbc.queryForObject("select message_id from inbox_message where trigger_id=?",(r,n)->UUID.fromString(r.getString(1)),trigger.toString());}
 @Test void oldMessageIsUnrecordedAndAnotherUserCannotInspect(){repo.locked(owner,()->{insertTrigger();return null;});var service=new AlertMailViewService(repo);var v=service.get(owner,message());assertEquals("NOT_RECORDED",v.state());assertNull(v.deliveryId());assertEquals("RESOURCE_NOT_FOUND",assertThrows(BusinessException.class,()->service.get(UUID.randomUUID(),message())).code());assertEquals("RESOURCE_NOT_FOUND",assertThrows(BusinessException.class,()->service.get(owner,UUID.randomUUID())).code());assertEquals(0,count("alert_mail_delivery"));}
 @Test void statusReadDoesNotClaimOrSendAndOmitsRecipient(){preference(true,3);var d=capture(true,true);var service=new AlertMailViewService(repo);var v=service.get(owner,message());assertEquals("PENDING",v.state());assertEquals(0,v.attempts());assertNull(v.sentAt());assertNull(repo.find(d.deliveryId()).leaseToken());assertFalse(v.toString().contains("fixture@example.com"));}
 @Test void publicRouteAuthenticatesAndRejectsAmbiguousQueries()throws Exception{
  preference(true,3);capture(true,true);var access=mock(NotificationAccess.class);when(access.resolve("session")).thenReturn(owner);var mvc=MockMvcBuilders.standaloneSetup(new AlertMailController(new AlertMailViewService(repo))).setControllerAdvice(new ApiExceptionHandler()).addFilters(new NotificationSecurityFilter(access,new ObjectMapper(),"http://localhost:15173")).build();String path="/api/v1/messages/"+message()+"/email-delivery";
  mvc.perform(get(path)).andExpect(status().isUnauthorized());mvc.perform(get(path).cookie(new Cookie("AISTOCK_SESSION","session"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("PENDING")).andExpect(jsonPath("$.data.email").doesNotExist()).andExpect(jsonPath("$.data.accountRevision").doesNotExist());
  for(String suffix:new String[]{"?ownerId=x","?a=1&a=2"})mvc.perform(get(path+suffix).cookie(new Cookie("AISTOCK_SESSION","session"))).andExpect(status().isBadRequest());
  mvc.perform(get(path).cookie(new Cookie("AISTOCK_SESSION","session")).header("Origin","http://localhost:15173","http://localhost:15173")).andExpect(status().isForbidden());
  mvc.perform(get(path+";evil").cookie(new Cookie("AISTOCK_SESSION","session"))).andExpect(status().isNotFound());
 }
}
