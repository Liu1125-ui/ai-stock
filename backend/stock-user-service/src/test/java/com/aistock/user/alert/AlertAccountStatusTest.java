package com.aistock.user.alert;
import com.aistock.user.account.UserRepository;import com.aistock.user.support.AuthDatabaseFixture;import com.fasterxml.jackson.databind.ObjectMapper;import java.util.UUID;import org.junit.jupiter.api.*;import org.springframework.test.web.servlet.setup.MockMvcBuilders;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@Tag("mysql") class AlertAccountStatusTest {
 @Test void authenticatesBeforeParsingAndReturnsOnlyCurrentStatus()throws Exception{
  var db=new AuthDatabaseFixture();var id=UUID.randomUUID();new UserRepository(db.jdbc()).insertVerifiedOrdinaryUser(id,"fixture@example.com","opaque-test-hash","测试用户",db.clock().instant());
  var mapper=new ObjectMapper().findAndRegisterModules().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  var mvc=MockMvcBuilders.standaloneSetup(new AlertAccountStatusController(db.jdbc(),db.clock())).addFilters(new AlertAccountTokenFilter("fixture-service-token",mapper)).build();
  String path="/internal/v1/users/"+id+"/alert-status";
  mvc.perform(get("/internal/v1/users/not-a-uuid/alert-status")).andExpect(status().isUnauthorized());
  mvc.perform(get(path).header("X-Service-Token","wrong")).andExpect(status().isUnauthorized());
  mvc.perform(get("/internal/v1/users/"+id+";probe=x/alert-status")).andExpect(status().isUnauthorized());
  mvc.perform(get(path).header("X-Service-Token","fixture-service-token","fixture-service-token")).andExpect(status().isUnauthorized());
  mvc.perform(get(path).header("X-Service-Token","fixture-service-token")).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ENABLED")).andExpect(jsonPath("$.data.userId").value(id.toString())).andExpect(jsonPath("$.data.email").doesNotExist()).andExpect(jsonPath("$.data.passwordHash").doesNotExist()).andExpect(jsonPath("$.data.checkedAt").exists());
  db.jdbc().update("update user_account set status='DISABLED' where user_id=?",id.toString());
  mvc.perform(get(path).header("X-Service-Token","fixture-service-token")).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DISABLED"));
 }
 @Test void missingAccountAndInvalidUuidAreControlled()throws Exception{
  var db=new AuthDatabaseFixture();var mapper=new ObjectMapper().findAndRegisterModules();var mvc=MockMvcBuilders.standaloneSetup(new AlertAccountStatusController(db.jdbc(),db.clock())).addFilters(new AlertAccountTokenFilter("fixture-service-token",mapper)).build();
  mvc.perform(get("/internal/v1/users/"+UUID.randomUUID()+"/alert-status").header("X-Service-Token","fixture-service-token")).andExpect(status().isNotFound());
  mvc.perform(get("/internal/v1/users/not-a-uuid/alert-status").header("X-Service-Token","fixture-service-token")).andExpect(status().isBadRequest());
 }
 @Test void emptyConfiguredTokenNeverAllowsAccess()throws Exception{
  var db=new AuthDatabaseFixture();var mvc=MockMvcBuilders.standaloneSetup(new AlertAccountStatusController(db.jdbc(),db.clock())).addFilters(new AlertAccountTokenFilter("",new ObjectMapper())).build();
  mvc.perform(get("/internal/v1/users/"+UUID.randomUUID()+"/alert-status").header("X-Service-Token","anything")).andExpect(status().isUnauthorized());
 }
 @Test void databaseFailureIs503WithoutRawException()throws Exception{
  var jdbc=org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);org.mockito.Mockito.when(jdbc.query(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<UserRepository.Account>>any(),org.mockito.ArgumentMatchers.<Object[]>any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private database detail"));
  var mvc=MockMvcBuilders.standaloneSetup(new AlertAccountStatusController(jdbc,java.time.Clock.systemUTC())).addFilters(new AlertAccountTokenFilter("fixture-service-token",new ObjectMapper())).build();
  mvc.perform(get("/internal/v1/users/"+UUID.randomUUID()+"/alert-status").header("X-Service-Token","fixture-service-token")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("ALERT_ACCOUNT_UNAVAILABLE")).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private database detail"))));
 }
}
