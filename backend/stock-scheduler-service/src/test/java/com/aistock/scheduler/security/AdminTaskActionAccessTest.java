package com.aistock.scheduler.security;
import com.aistock.common.*;import com.aistock.scheduler.admin.*;import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;import java.time.*;import java.util.*;
import org.junit.jupiter.api.*;import org.springframework.test.web.servlet.*;import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class AdminTaskActionAccessTest {
 final String path="/api/v1/admin/tasks/00000000-0000-0000-0000-000000000002/cancel";
 SessionIdentityClient identity;AdminTaskActionService service;MockMvc mvc;
 @BeforeEach void init(){identity=mock(SessionIdentityClient.class);service=mock(AdminTaskActionService.class);mvc=MockMvcBuilders.standaloneSetup(new AdminTaskActionController(service,Clock.systemUTC())).setControllerAdvice(new ApiExceptionHandler()).addFilters(new AdminTaskAccessFilter(identity,"server-token",new ObjectMapper().findAndRegisterModules())).build();}
 void role(String role){when(identity.resolve(any(),anyString())).thenReturn(ApiResponse.success(new SessionIdentityClient.Identity(UUID.randomUUID(),role,false,1),"trace"));}
 org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder submit(){return post(path).cookie(new Cookie("AISTOCK_SESSION","session")).header("Origin","http://127.0.0.1:15173").header("X-Aistock-Admin-Action","1").contentType("application/json").content("{}");}
 @Test void anonymousUserRevokedAndAuthorityFailureCannotExecute()throws Exception {
  mvc.perform(post(path).header("Origin","http://127.0.0.1:15173").header("X-Aistock-Admin-Action","1").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
  role("USER");mvc.perform(submit().header("X-Role","ADMIN").header("X-Service-Token","forged")).andExpect(status().isForbidden());
  when(identity.resolve(any(),anyString())).thenReturn(new ApiResponse<SessionIdentityClient.Identity>("AUTH_REQUIRED","登录失效",null,"trace"));mvc.perform(submit()).andExpect(status().isUnauthorized());
  when(identity.resolve(any(),anyString())).thenThrow(new IllegalStateException("private-token"));mvc.perform(submit()).andExpect(status().isServiceUnavailable());verifyNoInteractions(service);
 }
 @Test void missingNullAndUntrustedOriginOrDuplicateHeaderFailBeforeAnyWrite()throws Exception {
  role("ADMIN");
  for(String origin:List.of("null","https://outside.example","http://127.0.0.1:15173.evil","http://127.0.0.1:15173/"))mvc.perform(submit().with(r->{r.removeHeader("Origin");r.addHeader("Origin",origin);return r;})).andExpect(status().isForbidden());
  mvc.perform(post(path).cookie(new Cookie("AISTOCK_SESSION","session")).header("X-Aistock-Admin-Action","1").contentType("application/json").content("{}")).andExpect(status().isForbidden());
  mvc.perform(submit().header("X-Aistock-Admin-Action","1")).andExpect(status().isForbidden());
  mvc.perform(submit().contentType("text/plain")).andExpect(status().isBadRequest());verifyNoInteractions(service);
 }
 @Test void wrongMethodsAndRoutesAreNotWriteBackdoors()throws Exception {
  role("ADMIN");mvc.perform(submit().with(r->{r.setRequestURI("/api/v1/admin/tasks");return r;})).andExpect(status().isNotFound());
  mvc.perform(get(path).cookie(new Cookie("AISTOCK_SESSION","session"))).andExpect(status().isNotFound());
  mvc.perform(head(path).cookie(new Cookie("AISTOCK_SESSION","session"))).andExpect(status().isNotFound());
  mvc.perform(delete(path).cookie(new Cookie("AISTOCK_SESSION","session"))).andExpect(status().isNotFound());verifyNoInteractions(service);
 }
 @Test void exactPostPreflightDoesNotResolveSessionOrReadTask()throws Exception {
  var config=new AdminTaskSecurityConfiguration();var cors=config.adminTaskCors("http://127.0.0.1:15173").getFilter();
  var local=MockMvcBuilders.standaloneSetup(new AdminTaskActionController(service,Clock.systemUTC())).addFilters(cors,new AdminTaskAccessFilter(identity,"server-token",new ObjectMapper())).build();
  local.perform(options(path).header("Origin","http://127.0.0.1:15173").header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","content-type,x-aistock-admin-action")).andExpect(status().isOk());
  local.perform(options("/api/v1/admin/tasks").header("Origin","http://127.0.0.1:15173").header("Access-Control-Request-Method","POST")).andExpect(status().isForbidden());verifyNoInteractions(service,identity);
 }

 @Test void trainingListAndCancelUseSameAdminBoundary()throws Exception {
  var training=mock(AdminTrainingTasks.class);
  var local=MockMvcBuilders.standaloneSetup(new AdminTrainingTaskController(training))
      .setControllerAdvice(new ApiExceptionHandler()).addFilters(new AdminTaskAccessFilter(identity,"server-token",new ObjectMapper())).build();
  String list="/api/v1/admin/tasks/training";
  String cancel=list+"/00000000-0000-0000-0000-000000000002/cancel";
  var session=new Cookie("AISTOCK_SESSION","session");
  local.perform(get(list)).andExpect(status().isUnauthorized());
  role("USER");
  local.perform(get(list).cookie(session)).andExpect(status().isForbidden());
  local.perform(post(cancel).cookie(session).header("Origin","http://127.0.0.1:15173")
      .header("X-Aistock-Admin-Action","1").contentType("application/json").content("{}")).andExpect(status().isForbidden());
  verifyNoInteractions(training);
  role("ADMIN");
  when(training.list(1)).thenReturn(new AdminTrainingTasks.Page(List.of(),false,false));
  local.perform(get(list).cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.actionsEnabled").value(false));
 }
}
