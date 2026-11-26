package com.aistock.prediction.modelcatalog;
import com.aistock.prediction.verification.*;import java.time.*;import java.sql.Timestamp;import java.util.*;
import org.junit.jupiter.api.*;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.context.annotation.*;import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;import org.springframework.test.context.web.WebAppConfiguration;import org.springframework.mock.web.MockCookie;import org.springframework.web.context.WebApplicationContext;import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@Tag("mysql") @WebAppConfiguration @SpringJUnitConfig(VerificationHistoryHttpTest.Config.class)
class VerificationHistoryHttpTest {
 @Configuration @Import({ModelHttpTest.Config.class,VerificationHistoryController.class}) static class Config {
  @Bean VerificationQueryRepository query(ModelDatabaseFixture db,com.fasterxml.jackson.databind.ObjectMapper mapper){return new VerificationQueryRepository(db.jdbc,mapper,Clock.fixed(Instant.parse("2026-10-05T14:00:00Z"),ZoneOffset.UTC),20);}
 }
 @Autowired WebApplicationContext context;@Autowired ModelDatabaseFixture db;
 @BeforeEach void clean(){new ModelDatabaseFixture();}
 @Test void anonymousForgedHeadersAndAdminCannotReadOtherOwner()throws Exception{
  var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();String id=UUID.randomUUID().toString();var uri="/api/v1/predictions/"+id+"/verifications";
  var snapshot="{\"prediction\":{\"points\":[{\"step\":1,\"targetDate\":\"2026-10-08\",\"referencePrice\":\"25.800000\"}]}}";
  db.jdbc.update("insert into forecast_request(prediction_id,user_id,operation_id,request_hash,model_id,instrument_id,horizon,state,model_json,snapshot_json,hidden,created_at,expires_at) values(?,?,?,?,?,'SH600030',5,'SUCCEEDED','{}',?,1,?,?)",id,UUID.nameUUIDFromBytes("user-session".getBytes()).toString(),UUID.randomUUID().toString(),"a".repeat(64),UUID.randomUUID().toString(),snapshot,Timestamp.from(Instant.now()),Timestamp.from(Instant.now().plusSeconds(90)));
  mvc.perform(get(uri).header("X-Role","ADMIN").header("X-Service-Token",ModelHttpTest.SERVICE)).andExpect(status().isUnauthorized());
  mvc.perform(get(uri).cookie(new MockCookie("AISTOCK_SESSION","user-session"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.current").isEmpty()).andExpect(jsonPath("$.data.expectedPoints[0].state").value("WAITING"));
  for(var token:List.of("other-session","admin-session")){mvc.perform(get(uri).cookie(new MockCookie("AISTOCK_SESSION",token))).andExpect(status().isNotFound());mvc.perform(get(uri+"/"+UUID.randomUUID()).cookie(new MockCookie("AISTOCK_SESSION",token))).andExpect(status().isNotFound());}
  mvc.perform(get(uri+"?page=0").cookie(new MockCookie("AISTOCK_SESSION","user-session"))).andExpect(status().isBadRequest());
 }
}
