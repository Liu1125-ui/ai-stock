package com.aistock.scheduler.admin;
import com.aistock.common.*;import com.aistock.scheduler.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;import java.time.*;import java.util.*;
import org.junit.jupiter.api.*;import org.springframework.test.web.servlet.*;import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.aistock.scheduler.admin.AdminTaskActionRecords.*;
class AdminTaskActionHttpTest {
 static final UUID ACTOR=UUID.fromString("00000000-0000-0000-0000-000000000001"), TASK=UUID.fromString("00000000-0000-0000-0000-000000000002"), OP=UUID.fromString("00000000-0000-0000-0000-000000000003");
 static final Instant NOW=Instant.parse("2026-10-20T12:00:00Z");
 static final String ROOT="/api/v1/admin/tasks/"+TASK;
 static final String BODY="{\"operationId\":\""+OP+"\",\"expectedRevision\":\"0\",\"reasonCode\":\"CANCEL_NOT_NEEDED\"}";
 AdminTaskActionService service;MockMvc mvc;
 @BeforeEach void init(){
  service=mock(AdminTaskActionService.class);var identity=mock(SessionIdentityClient.class);
  when(identity.resolve(any(),anyString())).thenReturn(ApiResponse.success(new SessionIdentityClient.Identity(ACTOR,"ADMIN",false,1),"t"));
  mvc=MockMvcBuilders.standaloneSetup(new AdminTaskActionController(service,Clock.fixed(NOW,ZoneOffset.UTC)))
   .setControllerAdvice(new ApiExceptionHandler()).addFilters(new AdminTaskAccessFilter(identity,"server-token",new ObjectMapper().findAndRegisterModules())).build();
 }
 org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder submit(){return post(ROOT+"/cancel").cookie(new Cookie("AISTOCK_SESSION","session")).header("Origin","http://127.0.0.1:15173").header("X-Aistock-Admin-Action","1").contentType("application/json").content(BODY);}
 Receipt receipt(String outcome,String code){return new Receipt(OP.toString(),TASK.toString(),"CANCEL",outcome,code,"固定说明","QUEUED","APPLIED".equals(outcome)?"CANCELLED":"QUEUED","0","APPLIED".equals(outcome)?"1":"0",NOW,NOW);}
 @Test void validCommandUsesResolvedActorAndPreservesSuccessAndConflictReceipts()throws Exception {
  for(boolean applied:List.of(true,false)) {
   String code=applied?"OK":"TASK_REVISION_CONFLICT";
   when(service.execute(eq(ACTOR),eq(TASK),eq(Action.CANCEL),any(),eq(NOW))).thenReturn(new CommandResult(applied?200:409,code,receipt(applied?"APPLIED":"REJECTED",applied?"TASK_CANCEL_APPLIED":code)));
   mvc.perform(submit()).andExpect(status().is(applied?200:409)).andExpect(jsonPath("$.data.operationId").value(OP.toString())).andExpect(header().string("Cache-Control","no-store"));
  }
  verify(service,times(2)).execute(eq(ACTOR),eq(TASK),eq(Action.CANCEL),eq(new Command(OP,"0","CANCEL_NOT_NEEDED")),eq(NOW));
 }
 @Test void invalidBodyAndUnexpectedQueryNeverReachCommands()throws Exception {
  for(String body:List.of(BODY.substring(0,BODY.length()-1)+",\"note\":null}",BODY.substring(0,BODY.length()-1)+",\"operationId\":\""+OP+"\"}"," ".repeat(4097),"{} {}"))mvc.perform(submit().content(body)).andExpect(status().isBadRequest());
  mvc.perform(submit().param("ignored","x")).andExpect(status().isBadRequest());verifyNoInteractions(service);
 }
 @Test void boundedReadContractsAndReceiptAreAvailable()throws Exception {
  var denied=new Decision(false,"TASK_ACTIONS_DISABLED","入口关闭",null);
  when(service.options(eq(TASK),eq(NOW))).thenReturn(new Options(TASK.toString(),"0",NOW,false,denied,denied));
  when(service.receipt(ACTOR,TASK,OP)).thenReturn(receipt("APPLIED","TASK_CANCEL_APPLIED"));
  when(service.history(eq(TASK),any(),isNull(),eq(NOW))).thenReturn(new HistoryPage(List.of(),null,false,NOW));
  mvc.perform(get(ROOT+"/action-options").cookie(new Cookie("AISTOCK_SESSION","session"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.actionsEnabled").value(false));
  mvc.perform(get(ROOT+"/operations/"+OP).cookie(new Cookie("AISTOCK_SESSION","session"))).andExpect(status().isOk());
  mvc.perform(get(ROOT+"/operations").param("limit","20").cookie(new Cookie("AISTOCK_SESSION","session"))).andExpect(status().isOk());
  mvc.perform(get(ROOT+"/operations").param("limit","20","20").cookie(new Cookie("AISTOCK_SESSION","session"))).andExpect(status().isBadRequest());
 }
}
