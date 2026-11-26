package com.aistock.gateway;
import com.aistock.common.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserApiProxyControllerTest {
    @Test void frameworkCorsRejectionRemainsForbiddenWithControlledJson() throws Exception {
        var client=mock(UserPublicClient.class);
        var request=feign.Request.create(feign.Request.HttpMethod.POST,"http://user/api/v1/auth/logout",java.util.Map.of(),null,java.nio.charset.StandardCharsets.UTF_8,new feign.RequestTemplate());
        var remote=feign.Response.builder().request(request).status(403).reason("Forbidden").headers(java.util.Map.of())
                .body("Invalid CORS request",java.nio.charset.StandardCharsets.UTF_8).build();
        when(client.post(anyString(),anyMap(),anyMap(),any(byte[].class))).thenThrow(feign.FeignException.errorStatus("logout",remote));
        var mvc=MockMvcBuilders.standaloneSetup(new UserApiProxyController(client,new com.fasterxml.jackson.databind.ObjectMapper())).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(post("/api/v1/auth/logout").header("Origin","https://untrusted.example"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED")).andExpect(jsonPath("$.data").doesNotExist());
    }
    @Test void proxyPreservesMultipleCookiesAndRemovesSpoofedInternalHeaders() throws Exception {
        var client=mock(UserPublicClient.class);
        when(client.post(anyString(),anyMap(),anyMap(),any(byte[].class))).thenAnswer(call->{
            java.util.Map<String,String> headers=call.getArgument(2);
            assertFalse(headers.containsKey("X-Service-Token"));assertFalse(headers.containsKey("X-User-Id"));assertFalse(headers.containsKey("X-Role"));
            assertEquals("http://127.0.0.1:15173",headers.get("Origin"));
            var responseHeaders=new HttpHeaders();responseHeaders.add("Set-Cookie","AISTOCK_SESSION=test; HttpOnly; SameSite=Lax");responseHeaders.add("Set-Cookie","XSRF-TOKEN=; Max-Age=0");responseHeaders.setContentType(MediaType.APPLICATION_JSON);
            return ResponseEntity.ok().headers(responseHeaders).body("{\"code\":\"OK\",\"data\":{}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        });
        var mvc=MockMvcBuilders.standaloneSetup(new UserApiProxyController(client,new com.fasterxml.jackson.databind.ObjectMapper())).setControllerAdvice(new ApiExceptionHandler()).build();
        var response=mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{}")
                .header("X-Service-Token","forged").header("X-User-Id","fake").header("X-Role","ADMIN").header("Origin","http://127.0.0.1:15173"))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertEquals(2,response.getHeaders("Set-Cookie").size());
    }
    @Test void unknownPathsAndUnavailableUserServiceAreControlled() throws Exception {
        var client=mock(UserPublicClient.class);
        when(client.get(anyString(),anyMap(),anyMap())).thenThrow(new IllegalStateException("unavailable test backend"));
        var mvc=MockMvcBuilders.standaloneSetup(new UserApiProxyController(client,new com.fasterxml.jackson.databind.ObjectMapper())).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/api/v1/auth/unknown")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("USER_SERVICE_UNAVAILABLE")).andExpect(jsonPath("$.data").doesNotExist());
    }
}
