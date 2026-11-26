package com.aistock.prediction.modelcatalog;
import com.aistock.prediction.forecast.*;import com.fasterxml.jackson.databind.*;import java.time.*;import java.util.*;import org.junit.jupiter.api.*;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.context.annotation.*;import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;import org.springframework.test.context.web.WebAppConfiguration;import org.springframework.mock.web.MockCookie;import org.springframework.web.context.WebApplicationContext;import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@Tag("mysql") @WebAppConfiguration @SpringJUnitConfig(ForecastHttpTest.Config.class)
class ForecastHttpTest {
 @Configuration @Import({ModelHttpTest.Config.class,ForecastController.class,ForecastFusionController.class})static class Config {
  @Bean com.aistock.prediction.verification.VerificationQueryRepository verificationQuery(ModelDatabaseFixture db,ObjectMapper mapper){
   return new com.aistock.prediction.verification.VerificationQueryRepository(db.jdbc,mapper,Clock.fixed(ForecastMysqlTest.NOW,ZoneOffset.UTC),20);
  }
  @Bean ForecastRepository records(ModelDatabaseFixture db,ObjectMapper mapper){return new ForecastRepository(db.jdbc,mapper,Clock.fixed(ForecastMysqlTest.NOW,ZoneOffset.UTC));}
  @Bean ForecastService forecasts(ForecastRepository records,ModelRepository models,ObjectMapper mapper){return new ForecastService(records,models,new ForecastDependencies(){public JsonNode prepare(String stock,UUID op){return ForecastMysqlTest.window();}public JsonNode infer(ModelRecords.Version v,JsonNode w){return ForecastMysqlTest.result(v,w);}},mapper,Clock.fixed(ForecastMysqlTest.NOW,ZoneOffset.UTC));}
 }
 @Autowired WebApplicationContext context;@Autowired ModelCatalogService catalog;@Autowired ObjectMapper mapper;@Autowired ModelDatabaseFixture db;
 @BeforeEach void reset(){new ModelDatabaseFixture();}
 @Test void sessionOriginCsrfAndUnknownOwnerAreEnforced()throws Exception{var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();mvc.perform(get("/api/v1/predictions").header("X-Service-Token",ModelHttpTest.SERVICE).header("X-Role","ADMIN")).andExpect(status().isUnauthorized());var user=new MockCookie("AISTOCK_SESSION","user-session");mvc.perform(post("/api/v1/predictions").cookie(user).contentType("application/json").content("{}")).andExpect(status().isForbidden());var csrf=new MockCookie("XSRF-TOKEN","fixture-csrf");mvc.perform(post("/api/v1/predictions").cookie(user,csrf).header("Origin","http://127.0.0.1:15173").header("X-XSRF-TOKEN",csrf.getValue()).contentType("application/json").content("{\"ownerId\":\"forged\"}")).andExpect(status().isBadRequest());}
 @Test void successAndHistoryAreStrictlyPersonalIncludingAdmin()throws Exception{var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();var v=catalog.register(ModelCatalogMysqlTest.candidate(42));db.jdbc.update("update model_version set status='PUBLISHED',revision=1 where model_id=?",v.modelId().toString());var input=new ForecastRecords.Input(UUID.randomUUID().toString(),v.modelId().toString(),"SH600030",5);var user=new MockCookie("AISTOCK_SESSION","user-session");var csrf=new MockCookie("XSRF-TOKEN","fixture-csrf");var response=mvc.perform(post("/api/v1/predictions").cookie(user,csrf).header("Origin","http://127.0.0.1:15173").header("X-XSRF-TOKEN",csrf.getValue()).contentType("application/json").content(mapper.writeValueAsString(input))).andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("SUCCEEDED")).andReturn();String id=mapper.readTree(response.getResponse().getContentAsString()).path("data").path("predictionId").asText();mvc.perform(get("/api/v1/predictions/"+id).cookie(new MockCookie("AISTOCK_SESSION","other-session"))).andExpect(status().isNotFound());mvc.perform(get("/api/v1/predictions/"+id).cookie(new MockCookie("AISTOCK_SESSION","admin-session"))).andExpect(status().isNotFound());mvc.perform(get("/api/v1/predictions").cookie(user)).andExpect(jsonPath("$.data.total").value(1));}

 @Test void fusionRejectsMalformedWeightsAndProtectsOwner()throws Exception {
  var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  String path="/api/v1/predictions/fusion-preview";
  var user=new MockCookie("AISTOCK_SESSION","user-session");
  var csrf=new MockCookie("XSRF-TOKEN","fixture-csrf");
  String pair="""
    {"items":[{"predictionId":"00000000-0000-0000-0000-000000000001","weightBps":5000},
    {"predictionId":"00000000-0000-0000-0000-000000000002","weightBps":5000}]}
    """;
  mvc.perform(post(path).cookie(user).contentType("application/json").content(pair))
      .andExpect(status().isForbidden());
  mvc.perform(post(path).cookie(user,csrf).header("Origin","http://127.0.0.1:15173")
      .header("X-XSRF-TOKEN",csrf.getValue()).contentType("application/json").content(pair.replace("5000","5000.0")))
      .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FUSION_REQUEST_INVALID"));
  mvc.perform(post(path).cookie(user,csrf).header("Origin","http://127.0.0.1:15173")
      .header("X-XSRF-TOKEN",csrf.getValue()).contentType("application/json").content(pair))
      .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
 }
}
