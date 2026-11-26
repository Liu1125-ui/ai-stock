package com.aistock.user.position;
import com.aistock.common.ApiExceptionHandler;import com.aistock.user.security.SecurityConfiguration;import com.aistock.user.session.*;
import org.junit.jupiter.api.*;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.context.annotation.*;import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;import org.springframework.test.context.web.WebAppConfiguration;import org.springframework.web.context.WebApplicationContext;import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.*;import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@Tag("mysql") @WebAppConfiguration @SpringJUnitConfig(PositionControllerTest.Config.class)
class PositionControllerTest {
 @Configuration @EnableWebSecurity @org.springframework.web.servlet.config.annotation.EnableWebMvc @Import({SecurityConfiguration.class,AuthController.class,PositionController.class,ApiExceptionHandler.class})
 static class Config {
  @Bean(destroyMethod="close") PositionTestFixture fixture(){return new PositionTestFixture();}
  @Bean PositionService positions(PositionTestFixture f){return f.service;}
  @Bean SessionService sessions(PositionTestFixture f){return new SessionService(f.db.jdbc(),new byte[32],f.db.clock());}
 }
 @Autowired WebApplicationContext context;@Autowired PositionTestFixture f;@Autowired SessionService sessions;
 MockMvc mvc;MockCookie session,csrf;
 @BeforeEach void setup()throws Exception{
  mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  f.db.jdbc().update("update user_account set password_hash=? where user_id=?",org.springframework.security.crypto.bcrypt.BCrypt.hashpw("fixture-password",org.springframework.security.crypto.bcrypt.BCrypt.gensalt(4)),f.owner.userId().toString());
  session=new MockCookie("AISTOCK_SESSION",sessions.login(f.owner.userId()+"@example.com","fixture-password","position-http").rawToken());
  csrf=MockCookie.parse(mvc.perform(get("/api/v1/auth/csrf").cookie(session)).andReturn().getResponse().getHeader("Set-Cookie"));
 }
 MockHttpServletRequestBuilder write(MockHttpServletRequestBuilder b,String body){return b.cookie(session,csrf).header("Origin","http://127.0.0.1:15173").header("X-XSRF-TOKEN",csrf.getValue()).contentType("application/json").content(body);}
 @Test void authenticatedLifecycleReplaysDeletionAndRejectsOtherOwner()throws Exception{
  String request=java.util.UUID.randomUUID().toString();String body="{\"requestId\":\""+request+"\",\"instrumentId\":\"SH600030\",\"quantity\":\"1\",\"averageCost\":\"123.456789\"}";
  var created=mvc.perform(write(post("/api/v1/users/me/positions"),body).header("X-User-Id",f.other.userId().toString()).header("X-Role","ADMIN")).andExpect(status().isCreated()).andExpect(jsonPath("$.data.position.averageCost").value("123.456789")).andReturn().getResponse();
  var json=new com.fasterxml.jackson.databind.ObjectMapper();String id=json.readTree(created.getContentAsString()).path("data").path("positionId").asText();
  mvc.perform(get("/api/v1/users/me/positions").cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1));
  String delRequest=java.util.UUID.randomUUID().toString();String deletion="{\"requestId\":\""+delRequest+"\",\"expectedRevision\":\"1\"}";
  mvc.perform(write(delete("/api/v1/users/me/positions/"+id),deletion)).andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("DELETED"));
  mvc.perform(write(delete("/api/v1/users/me/positions/"+id),deletion)).andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("DELETED"));
  mvc.perform(get("/api/v1/users/me/positions/operations/"+delRequest).cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("DELETED"));
  f.db.jdbc().update("update user_account set password_hash=? where user_id=?",org.springframework.security.crypto.bcrypt.BCrypt.hashpw("fixture-password",org.springframework.security.crypto.bcrypt.BCrypt.gensalt(4)),f.other.userId().toString());
  var other=new MockCookie("AISTOCK_SESSION",sessions.login(f.other.userId()+"@example.com","fixture-password","other-position-http").rawToken());
  mvc.perform(get("/api/v1/users/me/positions/operations/"+delRequest).cookie(other)).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/users/me/positions").cookie(other)).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
 }
 @Test void strictJsonAndQueryContractRejectsAmbiguousInput()throws Exception{
  String rid=java.util.UUID.randomUUID().toString();String prefix="{\"requestId\":\""+rid+"\",\"instrumentId\":\"SH600030\",\"quantity\":\"1\",\"averageCost\":\"1\"";
  for(String body:new String[]{prefix+",\"userId\":\"forged\"}",prefix+",\"quantity\":\"2\"}",prefix+"} {}",prefix.replace("\"quantity\":\"1\"","\"quantity\":1")+"}"})mvc.perform(write(post("/api/v1/users/me/positions"),body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.data").doesNotExist());
  mvc.perform(write(post("/api/v1/users/me/positions")," ".repeat(4097))).andExpect(status().isPayloadTooLarge());
  mvc.perform(get("/api/v1/users/me/positions?userId=forged").cookie(session)).andExpect(status().isBadRequest());
 }
 @Test void anonymousForgedHeadersAndUntrustedWritesAreRejected()throws Exception{
  mvc.perform(get("/api/v1/users/me/positions").header("X-User-Id",f.owner.userId().toString()).header("X-Role","ADMIN")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/v1/users/me/positions").cookie(session).header("Origin","http://127.0.0.1:15173").contentType("application/json").content("{}")).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/users/me/positions").cookie(session,csrf).header("X-XSRF-TOKEN",csrf.getValue()).header("Origin","https://untrusted.example").contentType("application/json").content("{}")).andExpect(status().isForbidden());
 }
}
