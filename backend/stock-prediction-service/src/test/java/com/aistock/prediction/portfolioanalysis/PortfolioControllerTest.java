package com.aistock.prediction.portfolioanalysis;
import com.aistock.common.*;import com.aistock.prediction.security.*;import com.aistock.prediction.modelcatalog.ModelRecords.Actor;
import java.util.*;import org.junit.jupiter.api.*;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.context.annotation.*;import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;import org.springframework.test.context.web.WebAppConfiguration;import org.springframework.web.context.WebApplicationContext;import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;import org.springframework.mock.web.MockCookie;import org.springframework.test.web.servlet.*;import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;import static org.mockito.Mockito.*;import static org.junit.jupiter.api.Assertions.*;
import static com.aistock.prediction.portfolioanalysis.PortfolioRecords.*;import static com.aistock.prediction.portfolioanalysis.PortfolioTestEvidence.*;
@WebAppConfiguration @SpringJUnitConfig(PortfolioControllerTest.Config.class)
class PortfolioControllerTest {
 @Configuration @EnableWebSecurity @org.springframework.web.servlet.config.annotation.EnableWebMvc @Import({PredictionSecurityConfiguration.class,PortfolioController.class,ApiExceptionHandler.class})
 static class Config {
  @Bean PortfolioService portfolio(){return mock(PortfolioService.class);}
  @Bean SessionIdentityClient identity(){return (input,token)->ApiResponse.success(new SessionIdentityClient.Identity(UUID.nameUUIDFromBytes(input.token().getBytes(java.nio.charset.StandardCharsets.UTF_8)),"USER",false,1),"test");}
  @Bean static org.springframework.context.support.PropertySourcesPlaceholderConfigurer properties(){var p=new org.springframework.context.support.PropertySourcesPlaceholderConfigurer();var v=new Properties();v.setProperty("aistock.internal.token","fixture-service");p.setProperties(v);return p;}
 }
 @Autowired WebApplicationContext context;@Autowired PortfolioService service;MockMvc mvc;String session="a".repeat(43);UUID id;MockCookie csrf=new MockCookie("XSRF-TOKEN","fixture-csrf");
 @BeforeEach void setup(){reset(service);id=UUID.randomUUID();mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();when(service.create(any(),any(),any())).thenReturn(new Creation(new Operation(id,"SUCCEEDED",UUID.randomUUID(),null,NOW,NOW.plusSeconds(30)),false));}
 org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postBody(String body){return post("/api/v1/portfolio-analyses").cookie(new MockCookie("AISTOCK_SESSION",session),csrf).header("Origin","http://127.0.0.1:15173").header("X-XSRF-TOKEN",csrf.getValue()).contentType("application/json").content(body);}
 @Test void authenticationCsrfAndOriginAreRequired()throws Exception{
  mvc.perform(get("/api/v1/portfolio-analyses").header("X-User-Id",OWNER.toString()).header("X-Role","ADMIN")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/v1/portfolio-analyses").cookie(new MockCookie("AISTOCK_SESSION",session)).content("{}")).andExpect(status().isForbidden());
  mvc.perform(postBody("{}").with(req->{req.removeHeader("Origin");req.addHeader("Origin","https://untrusted.example");return req;})).andExpect(status().isForbidden());verifyNoInteractions(service);
 }
 @Test void exactBodyAndQueryContractRejectsIdentityAndPriceInjection()throws Exception{
  String valid="{\"requestId\":\""+id+"\"}";
  for(String bad:new String[]{valid+" {}",valid.replace("}",",\"userId\":\"forged\"}"),valid.replace("}",",\"requestId\":\""+id+"\"}"),"{\"requestId\":1}","{}"})mvc.perform(postBody(bad)).andExpect(status().isBadRequest());
  mvc.perform(postBody(" ".repeat(1025))).andExpect(status().isPayloadTooLarge());mvc.perform(postBody(valid).queryParam("userId","forged")).andExpect(status().isBadRequest());verifyNoInteractions(service);
 }
 @Test void duplicateCookiesUseSameLastValueForActorAndPrivateSession()throws Exception{
  String last="b".repeat(43);mvc.perform(postBody("{\"requestId\":\""+id+"\"}").with(req->{req.setCookies(new MockCookie("AISTOCK_SESSION",session),new MockCookie("AISTOCK_SESSION",last),csrf);return req;})).andExpect(status().isCreated());
  verify(service).create(new Actor(UUID.nameUUIDFromBytes(last.getBytes(java.nio.charset.StandardCharsets.UTF_8)),"USER"),new SessionToken(last),new Request(id));assertFalse(new SessionToken(last).toString().contains(last));
 }
 @Test void firstReplayAndRunningHaveDistinctHttpStatus()throws Exception{
  String body="{\"requestId\":\""+id+"\"}";mvc.perform(postBody(body)).andExpect(status().isCreated());
  when(service.create(any(),any(),any())).thenReturn(new Creation(new Operation(id,"SUCCEEDED",UUID.randomUUID(),null,NOW,NOW.plusSeconds(30)),true));mvc.perform(postBody(body)).andExpect(status().isOk());
  when(service.create(any(),any(),any())).thenReturn(new Creation(new Operation(id,"RUNNING",null,null,NOW,NOW.plusSeconds(30)),true));mvc.perform(postBody(body)).andExpect(status().isAccepted());
 }
}
