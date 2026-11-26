package com.aistock.user.position;

import com.aistock.common.ApiExceptionHandler;
import com.aistock.user.session.*;
import java.time.Duration;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("mysql")
class PositionSnapshotControllerTest {
 private PositionTestFixture f;
 private SessionService sessions;
 private MockMvc mvc;
 private String userToken,adminToken;
 private static final String SERVICE="isolated-service-token";
 @BeforeEach void setup(){
  f=new PositionTestFixture();
  sessions=new SessionService(f.db.jdbc(),new byte[32],f.db.clock());
  userToken=login(f.owner);adminToken=login(f.other);
  mvc=endpoint(SERVICE);
 }
 @AfterEach void close(){if(f!=null)f.close();}
 private String login(Identity i){
  f.db.jdbc().update("update user_account set password_hash=? where user_id=?",org.springframework.security.crypto.bcrypt.BCrypt.hashpw("fixture-password",org.springframework.security.crypto.bcrypt.BCrypt.gensalt(4)),i.userId().toString());
  return sessions.login(i.userId()+"@example.com","fixture-password","snapshot-fixture").rawToken();
 }
 private MockMvc endpoint(String service){return MockMvcBuilders.standaloneSetup(new PositionSnapshotController(f.service,sessions,new com.fasterxml.jackson.databind.ObjectMapper(),service)).setControllerAdvice(new ApiExceptionHandler()).build();}
 private ResultActions call(String route,String token)throws Exception{return mvc.perform(post("/internal/v1/positions/"+route).header("X-Service-Token",SERVICE).contentType("application/json").content("{\"token\":\""+token+"\"}"));}
 @Test void realSessionsReturnOnlyTheirOwnConsistentBookForBothRoles()throws Exception{
  f.service.apply(f.owner,f.create("SH600030"));
  f.service.apply(f.other,f.create("SH600276"));
  call("snapshot",userToken).andExpect(status().isOk()).andExpect(jsonPath("$.data.ownerId").value(f.owner.userId().toString())).andExpect(jsonPath("$.data.sourceService").value("stock-user-service")).andExpect(jsonPath("$.data.bookRevision").value("1")).andExpect(jsonPath("$.data.items.length()").value(1)).andExpect(jsonPath("$.data.items[0].instrumentId").value("SH600030"));
  call("snapshot",adminToken).andExpect(status().isOk()).andExpect(jsonPath("$.data.ownerId").value(f.other.userId().toString())).andExpect(jsonPath("$.data.items[0].instrumentId").value("SH600276"));
  call("revision",userToken).andExpect(status().isOk()).andExpect(jsonPath("$.data.bookRevision").value("1")).andExpect(jsonPath("$.data.items").doesNotExist());
 }
 @Test void revokedExpiredMissingAndMalformedSessionsCannotRead()throws Exception{
  sessions.logout(userToken);call("snapshot",userToken).andExpect(status().isUnauthorized());
  f.db.clock().advance(Duration.ofHours(8));call("revision",adminToken).andExpect(status().isUnauthorized());
  mvc.perform(post("/internal/v1/positions/snapshot").header("X-Service-Token",SERVICE).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
  call("snapshot","wrong").andExpect(status().isUnauthorized());
 }
 @Test void internalAuthenticationPrecedesParsingAndMissingConfigurationFailsClosed()throws Exception{
  mvc.perform(post("/internal/v1/positions/snapshot").header("X-Service-Token","wrong").contentType("application/json").content("not-json")).andExpect(status().isUnauthorized());
  mvc.perform(post("/internal/v1/positions/revision").contentType("application/json").content("not-json")).andExpect(status().isUnauthorized());
  endpoint("").perform(post("/internal/v1/positions/snapshot").contentType("application/json").content("not-json")).andExpect(status().isServiceUnavailable());
 }
 @Test void rejectsAmbiguousExtraFieldsQueriesAndOversizedBodies()throws Exception{
  for(String body:new String[]{"{\"token\":\""+userToken+"\",\"userId\":\"forged\"}","{\"token\":\""+userToken+"\",\"token\":\""+adminToken+"\"}","{\"token\":\""+userToken+"\"} {}","{\"token\":1}"}){
   mvc.perform(post("/internal/v1/positions/snapshot").header("X-Service-Token",SERVICE).contentType("application/json").content(body)).andExpect(status().isBadRequest());
  }
  mvc.perform(post("/internal/v1/positions/snapshot?userId=forged").header("X-Service-Token",SERVICE).contentType("application/json").content("{\"token\":\""+userToken+"\"}")).andExpect(status().isBadRequest());
  mvc.perform(post("/internal/v1/positions/snapshot").header("X-Service-Token",SERVICE).contentType("application/json").content(" ".repeat(1025))).andExpect(status().isPayloadTooLarge());
  assertFalse(new PositionSnapshotRecords.SnapshotRequest(userToken).toString().contains(userToken));
 }
}
