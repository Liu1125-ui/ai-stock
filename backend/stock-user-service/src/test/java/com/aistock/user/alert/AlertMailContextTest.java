package com.aistock.user.alert;

import com.aistock.user.account.UserRepository;
import com.aistock.user.support.AuthDatabaseFixture;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@Tag("mysql") class AlertMailContextTest {
 private AuthDatabaseFixture db; private UUID id; private MockMvc mvc;
 @BeforeEach void setup() {
  db=new AuthDatabaseFixture(); id=UUID.randomUUID();
  new UserRepository(db.jdbc()).insertVerifiedOrdinaryUser(id,"fixture@example.com","opaque-test-hash","测试用户",db.clock().instant());
  var mapper=new ObjectMapper().findAndRegisterModules();
  mvc=MockMvcBuilders.standaloneSetup(new AlertMailContextController(db.jdbc(),db.clock()))
   .addFilters(new AlertAccountTokenFilter("fixture",mapper)).build();
 }
 String path(){return "/internal/v1/users/"+id;}
 @Test void contextContainsIdentityAnchorButNoRecipientOrCredentials()throws Exception {
  mvc.perform(get(path()+"/alert-context").header("X-Service-Token","fixture"))
   .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
   .andExpect(jsonPath("$.data.userId").value(id.toString())).andExpect(jsonPath("$.data.status").value("ENABLED"))
   .andExpect(jsonPath("$.data.revision").value(0)).andExpect(jsonPath("$.data.emailVerified").value(true))
   .andExpect(jsonPath("$.data.email").doesNotExist()).andExpect(content().string(not(containsString("opaque-test-hash"))));
 }
 @Test void onlyMatchingRevisionReturnsRecipientAndCurrentStatus()throws Exception {
  mvc.perform(get(path()+"/alert-mail-target?revision=0").header("X-Service-Token","fixture"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.data.email").value("fixture@example.com"));
  db.jdbc().update("update user_account set status='DISABLED',revision=1 where user_id=?",id.toString());
  mvc.perform(get(path()+"/alert-mail-target?revision=0").header("X-Service-Token","fixture"))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALERT_RECIPIENT_CHANGED"))
   .andExpect(content().string(not(containsString("fixture@example.com"))));
  mvc.perform(get(path()+"/alert-mail-target?revision=1").header("X-Service-Token","fixture"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DISABLED"));
 }
 @Test void futureVerificationDoesNotAuthorizeMail()throws Exception {
  db.jdbc().update("update user_account set email_verified_at=? where user_id=?",java.sql.Timestamp.from(db.clock().instant().plusSeconds(1)),id.toString());
  mvc.perform(get(path()+"/alert-context").header("X-Service-Token","fixture")).andExpect(status().isOk()).andExpect(jsonPath("$.data.emailVerified").value(false));
  mvc.perform(get(path()+"/alert-mail-target?revision=0").header("X-Service-Token","fixture")).andExpect(status().isOk()).andExpect(jsonPath("$.data.emailVerified").value(false)).andExpect(jsonPath("$.data.email").doesNotExist());
 }
 @Test void authenticationPrecedesValidationAndDuplicateHeadersAreRejected()throws Exception {
  for(String suffix:new String[]{"/alert-context","/alert-mail-target?revision=0"}) {
   mvc.perform(get(path()+suffix)).andExpect(status().isUnauthorized());
   mvc.perform(get(path()+suffix).header("X-Service-Token","fixture","fixture")).andExpect(status().isUnauthorized());
   mvc.perform(get(path()+suffix).header("X-Service-Token","wrong")).andExpect(status().isUnauthorized());
  }
  mvc.perform(get("/internal/v1/users/bad/alert-context")).andExpect(status().isUnauthorized());
 }
 @Test void invalidOrAmbiguousQueriesFailClosed()throws Exception {
  for(String suffix:new String[]{"/alert-mail-target","/alert-mail-target?revision=-1","/alert-mail-target?revision=0&revision=1","/alert-mail-target?revision=1.5","/alert-mail-target?revision=9223372036854775808","/alert-mail-target?revision=0&owner=x","/alert-context?email=true"})
   mvc.perform(get(path()+suffix).header("X-Service-Token","fixture")).andExpect(status().isBadRequest());
  mvc.perform(get("/internal/v1/users/bad/alert-context").header("X-Service-Token","fixture")).andExpect(status().isBadRequest());
  mvc.perform(get("/internal/v1/users/"+UUID.randomUUID()+"/alert-context").header("X-Service-Token","fixture")).andExpect(status().isNotFound());
 }
 @Test void databaseFailureDoesNotLeakRawDetails()throws Exception {
  var jdbc=org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
  org.mockito.Mockito.when(jdbc.query(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Object>>any(),org.mockito.ArgumentMatchers.<Object[]>any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private sql and recipient"));
  var failed=MockMvcBuilders.standaloneSetup(new AlertMailContextController(jdbc,db.clock())).build();
  failed.perform(get(path()+"/alert-context")).andExpect(status().isServiceUnavailable()).andExpect(content().string(not(containsString("private sql"))));
 }
}
