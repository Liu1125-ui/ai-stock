package com.aistock.gateway;
import com.aistock.common.ApiExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class ModelProxyTest {
    @Test void modelPathsReachPredictionInsteadOfBroadUserAdminRouteAndStripIdentity()throws Exception{
        var prediction=mock(PredictionPublicClient.class);var user=mock(UserPublicClient.class);var mapper=new ObjectMapper();
        when(prediction.get(anyString(),anyMap())).thenReturn(ResponseEntity.ok("{\"code\":\"OK\",\"data\":{}}".getBytes()));
        var mvc=MockMvcBuilders.standaloneSetup(new ModelApiProxyController(prediction,mapper),new UserApiProxyController(user,mapper)).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/api/v1/admin/models").header("Cookie","AISTOCK_SESSION=test-session").header("X-Role","ADMIN").header("X-Service-Token","forged-client-token")).andExpect(status().isOk());
        var headers=ArgumentCaptor.forClass(Map.class);verify(prediction).get(eq("admin/models"),headers.capture());assertFalse(headers.getValue().containsKey("X-Role"));assertFalse(headers.getValue().containsKey("X-Service-Token"));verifyNoInteractions(user);
        mvc.perform(post("/internal/v1/models/candidates")).andExpect(status().isNotFound());
        assertFalse(ProxyHeaderPolicy.allowed("POST","/api/v1/admin/models/not-a-uuid/publish"));assertFalse(ProxyHeaderPolicy.allowed("PATCH","/api/v1/admin/models"));
    }
    @Test void chunkedOversizedModelBodyIsRejectedWithoutReadingWholeStream()throws Exception{
        var prediction=mock(PredictionPublicClient.class);
        var mvc=MockMvcBuilders.standaloneSetup(new ModelApiProxyController(prediction,new ObjectMapper())).setControllerAdvice(new ApiExceptionHandler()).build();
        var reads=new java.util.concurrent.atomic.AtomicInteger();
        mvc.perform(context->{
            var request=new org.springframework.mock.web.MockHttpServletRequest(context,"POST","/api/v1/admin/models/00000000-0000-0000-0000-000000000001/publish"){
                @Override public int getContentLength(){return -1;}
                @Override public long getContentLengthLong(){return -1;}
                @Override public jakarta.servlet.ServletInputStream getInputStream(){return new jakarta.servlet.ServletInputStream(){
                    @Override public int read(){int n=reads.incrementAndGet();if(n>65537)throw new IllegalStateException("request exceeded bounded read");return 'x';}
                    @Override public boolean isFinished(){return false;}
                    @Override public boolean isReady(){return true;}
                    @Override public void setReadListener(jakarta.servlet.ReadListener listener){}
                };}
            };
            request.setContentType("application/json");return request;
        }).andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("MODEL_PAYLOAD_TOO_LARGE"));
        assertTrue(reads.get()<=65537);verifyNoInteractions(prediction);
    }
    @Test void plainTextCorsDenialRemains403()throws Exception{
        var prediction=mock(PredictionPublicClient.class);
        var upstream=feign.Response.builder().status(403).reason("Forbidden").headers(Map.of()).request(feign.Request.create(feign.Request.HttpMethod.POST,"http://prediction/api/v1/admin/models",Map.of(),new byte[0],java.nio.charset.StandardCharsets.UTF_8,null)).body("Invalid CORS request",java.nio.charset.StandardCharsets.UTF_8).build();
        var denied=feign.FeignException.errorStatus("model",upstream);
        when(prediction.post(anyString(),anyMap(),any())).thenThrow(denied);when(prediction.options(anyString(),anyMap())).thenThrow(denied);
        var mvc=MockMvcBuilders.standaloneSetup(new ModelApiProxyController(prediction,new ObjectMapper())).setControllerAdvice(new ApiExceptionHandler()).build();
        var path="/api/v1/admin/models/00000000-0000-0000-0000-000000000001/publish";
        mvc.perform(post(path).header("Origin","https://untrusted.example").contentType("application/json").content("{}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED")).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void unavailablePredictionGetsControlledFailure()throws Exception{
        var prediction=mock(PredictionPublicClient.class);when(prediction.get(anyString(),anyMap())).thenThrow(new IllegalStateException("private transport detail"));
        var mvc=MockMvcBuilders.standaloneSetup(new ModelApiProxyController(prediction,new ObjectMapper())).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/api/v1/models")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("PREDICTION_SERVICE_UNAVAILABLE"));
    }
}
