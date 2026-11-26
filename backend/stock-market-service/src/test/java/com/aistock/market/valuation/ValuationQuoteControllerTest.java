package com.aistock.market.valuation;
import com.aistock.common.ApiExceptionHandler;
import com.aistock.market.ingestion.CandidateRequestFilter;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class ValuationQuoteControllerTest {
 @Test void filterBoundsQuoteBodyBeforePassingItToController()throws Exception{
  var request=new MockHttpServletRequest("POST","/internal/v1/market/valuation-quotes");request.setServletPath(request.getRequestURI());request.addHeader("X-Service-Token","test");request.setContent(new byte[4097]);
  var response=new MockHttpServletResponse();var passed=new AtomicBoolean();new CandidateRequestFilter("test",2097152).doFilter(request,response,(r,s)->passed.set(true));
  assertEquals(413,response.getStatus());assertFalse(passed.get());
 }
 @Test void authenticationAndStrictJsonFailBeforeService()throws Exception{
  var service=mock(ValuationQuoteService.class);var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
  var mvc=MockMvcBuilders.standaloneSetup(new ValuationQuoteController(service,mapper)).addFilters(new CandidateRequestFilter("test",2097152)).setControllerAdvice(new ApiExceptionHandler()).build();
  mvc.perform(post("/internal/v1/market/valuation-quotes").content("not-json")).andExpect(status().isUnauthorized());
  String valid="{\"asOfDate\":\"2026-09-28\",\"instrumentIds\":[\"SH600030\"]}";
  for(String bad:new String[]{valid+" {}",valid.replace("}",",\"ownerId\":\"forged\"}"),valid.replace("}",",\"asOfDate\":\"2026-09-27\"}"),valid.replace("[\"SH600030\"]","[1]")}){
   mvc.perform(post("/internal/v1/market/valuation-quotes").header("X-Service-Token","test").contentType("application/json").content(bad)).andExpect(status().isBadRequest());
  }
  mvc.perform(post("/internal/v1/market/valuation-quotes?instrumentId=forged").header("X-Service-Token","test").contentType("application/json").content(valid)).andExpect(status().isBadRequest());verifyNoInteractions(service);
 }
 @Test void missingServiceConfigurationFailsClosed()throws Exception{
  var req=new MockHttpServletRequest("POST","/internal/v1/market/valuation-quotes");req.setServletPath(req.getRequestURI());req.setContent("not-json".getBytes());var res=new MockHttpServletResponse();
  new CandidateRequestFilter("",2097152).doFilter(req,res,(r,s)->fail("unconfigured authentication must not pass"));assertEquals(503,res.getStatus());
 }
}
