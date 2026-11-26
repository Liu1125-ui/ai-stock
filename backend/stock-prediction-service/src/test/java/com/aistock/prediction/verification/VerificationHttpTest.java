package com.aistock.prediction.verification;
import com.aistock.common.ApiExceptionHandler;import com.aistock.prediction.security.*;import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;import static org.mockito.Mockito.*;
class VerificationHttpTest {
 @Test void identityBeforeParsingAndBoundedStrictBody()throws Exception{
  var service=mock(VerificationService.class);var mapper=new ObjectMapper().findAndRegisterModules();var mvc=MockMvcBuilders.standaloneSetup(new VerificationController(service)).setControllerAdvice(new ApiExceptionHandler()).addFilters(new ModelRequestFilter("private-test",new SecurityErrorWriter(mapper))).build();
  String path="/internal/v1/predictions/11111111-1111-1111-1111-111111111111/verify";
  mvc.perform(post(path).contentType("application/json").content("bad")).andExpect(status().isUnauthorized());
  mvc.perform(post(path).header("X-Service-Token","private-test").contentType("application/json").content("x".repeat(4097))).andExpect(status().isPayloadTooLarge());
  mvc.perform(post(path).header("X-Service-Token","private-test").contentType("application/json").content("{\"extra\":1}")).andExpect(status().isBadRequest());
  mvc.perform(get("/internal/v1/prediction-verifications/operations/11111111-1111-1111-1111-111111111111")).andExpect(status().isUnauthorized());
  mvc.perform(get(path.replace("/verify","/verification"))).andExpect(status().isUnauthorized());verifyNoInteractions(service);
 }
}
