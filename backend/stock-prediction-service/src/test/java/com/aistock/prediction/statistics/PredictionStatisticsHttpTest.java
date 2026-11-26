package com.aistock.prediction.statistics;
import com.aistock.common.*;import com.aistock.prediction.security.*;import com.aistock.prediction.modelcatalog.StatisticsPredictionFixture;import java.time.*;import java.util.*;import org.junit.jupiter.api.*;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.context.annotation.*;import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;import org.springframework.test.context.web.WebAppConfiguration;import org.springframework.web.context.WebApplicationContext;import org.springframework.mock.web.MockCookie;import org.springframework.test.web.servlet.setup.MockMvcBuilders;import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebAppConfiguration @Tag("mysql") @SpringJUnitConfig(PredictionStatisticsHttpTest.Config.class)
class PredictionStatisticsHttpTest {
 @Configuration @EnableWebSecurity @org.springframework.web.servlet.config.annotation.EnableWebMvc @Import({PredictionSecurityConfiguration.class,PredictionStatisticsController.class,ApiExceptionHandler.class})
 static class Config {
  @Bean PredictionStatisticsRepository repository(){return new PredictionStatisticsRepository(StatisticsPredictionFixture.fresh());}
  @Bean Clock clock(){return PredictionStatisticsMysqlTest.CLOCK;}
  @Bean SessionIdentityClient identity(){return (input,token)->{
   if(input.token().equals("broken"))throw new IllegalStateException("private endpoint");
   if(input.token().equals("malformed"))return ApiResponse.success(new SessionIdentityClient.Identity(null,"ADMIN",false,-1),"test");
   return ApiResponse.success(new SessionIdentityClient.Identity(UUID.nameUUIDFromBytes(input.token().getBytes()),input.token().equals("admin")?"ADMIN":"USER",false,0),"test");};}
  @Bean static org.springframework.context.support.PropertySourcesPlaceholderConfigurer properties(){var p=new org.springframework.context.support.PropertySourcesPlaceholderConfigurer();var v=new Properties();v.put("aistock.internal.token","test-only");p.setProperties(v);return p;}
 }
 @Autowired WebApplicationContext context;
 @Test void rejectsNonAdminAmbiguousCookiesAndMalformedIdentity()throws Exception{
  var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();var path="/api/v1/admin/statistics/predictions";
  mvc.perform(get(path).header("X-Role","ADMIN")).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","no-store"));
  mvc.perform(get(path).cookie(new MockCookie("AISTOCK_SESSION","user"))).andExpect(status().isForbidden());
  var admin=new MockCookie("AISTOCK_SESSION","admin");mvc.perform(get(path).cookie(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.data.acceptedRequests").value("0"));
  mvc.perform(get(path).cookie(admin,admin)).andExpect(status().isUnauthorized());
  mvc.perform(get(path).cookie(new MockCookie("AISTOCK_SESSION","malformed"))).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.data").isEmpty());
  mvc.perform(get(path).cookie(new MockCookie("AISTOCK_SESSION","broken"))).andExpect(status().isServiceUnavailable());
  mvc.perform(get(path).cookie(admin).param("secret","x")).andExpect(status().isBadRequest());
 }
}
