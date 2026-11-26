package com.aistock.user.statistics;
import com.aistock.common.ApiExceptionHandler;import com.aistock.user.security.SecurityConfiguration;import com.aistock.user.session.*;import com.aistock.user.support.AuthDatabaseFixture;import java.time.Clock;import org.junit.jupiter.api.*;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.context.annotation.*;import org.springframework.security.config.annotation.web.builders.HttpSecurity;import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;import org.springframework.test.context.web.WebAppConfiguration;import org.springframework.web.context.WebApplicationContext;import org.springframework.mock.web.MockCookie;import org.springframework.test.web.servlet.MockMvc;import org.springframework.test.web.servlet.setup.MockMvcBuilders;import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@Tag("mysql") @WebAppConfiguration @SpringJUnitConfig(UserStatisticsHttpTest.Config.class)
class UserStatisticsHttpTest {
 @Configuration @EnableWebSecurity @org.springframework.web.servlet.config.annotation.EnableWebMvc @Import({SecurityConfiguration.class,UserStatisticsController.class,ApiExceptionHandler.class})
 static class Config {
  @Bean AuthDatabaseFixture db(){return new AuthDatabaseFixture();}
  @Bean SessionService sessions(AuthDatabaseFixture db){return new SessionService(db.jdbc(),new byte[32],db.clock());}
  @Bean UserStatisticsRepository statistics(AuthDatabaseFixture db){return new UserStatisticsRepository(db.jdbc());}
  @Bean Clock clock(AuthDatabaseFixture db){return db.clock();}
 }
 @Autowired WebApplicationContext context;@Autowired AuthDatabaseFixture db;@Autowired SessionService sessions;MockMvc mvc;MockCookie admin,member;
 @BeforeEach void setup(){db.jdbc().update("delete from login_session");db.jdbc().update("delete from user_account");UserStatisticsData.account(db,"admin@example.com","ADMIN","ENABLED",db.clock().instant());UserStatisticsData.account(db,"member@example.com","USER","ENABLED",db.clock().instant());admin=new MockCookie("AISTOCK_SESSION",sessions.login("admin@example.com",UserStatisticsData.PASSWORD,"test").rawToken());member=new MockCookie("AISTOCK_SESSION",sessions.login("member@example.com",UserStatisticsData.PASSWORD,"test").rawToken());mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
 @Test void administratorOnlyNoStoreAndNoPrivateContent()throws Exception{
  mvc.perform(get("/api/v1/admin/statistics/users")).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","no-store"));
  mvc.perform(get("/api/v1/admin/statistics/users").cookie(member).header("X-Role","ADMIN")).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/admin/statistics/users").cookie(admin)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.data.totalUsers").value("2")).andExpect(jsonPath("$.data.meta.window.timeZone").value("Asia/Shanghai")).andExpect(jsonPath("$.data.email").doesNotExist());
 }
 @Test void duplicateSessionAndQueryAreRejected()throws Exception{
  mvc.perform(get("/api/v1/admin/statistics/users").cookie(admin,admin)).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/admin/statistics/users").cookie(admin).with(r->{r.addHeader("Cookie","x=1");r.addHeader("Cookie","y=2");org.junit.jupiter.api.Assertions.assertTrue(java.util.Collections.list(r.getHeaders("Cookie")).size()>1);return r;})).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/admin/statistics/users").cookie(admin).param("from","2026-09-30","2026-09-30").param("to","2026-09-30")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("STATISTICS_QUERY_INVALID"));
 }
 @Test void downgradedAndDisabledAdminCannotReadOldSession()throws Exception{
  db.jdbc().update("update user_account set role_code='USER' where email_normalized='admin@example.com'");mvc.perform(get("/api/v1/admin/statistics/users").cookie(admin)).andExpect(status().isForbidden());
  db.jdbc().update("update user_account set status='DISABLED' where email_normalized='admin@example.com'");mvc.perform(get("/api/v1/admin/statistics/users").cookie(admin)).andExpect(status().isUnauthorized());
 }
}
