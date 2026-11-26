package com.aistock.user.security;

import com.aistock.common.ApiExceptionHandler;
import com.aistock.user.session.*;
import com.aistock.user.support.AuthDatabaseFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.bind.annotation.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@Tag("mysql") @WebAppConfiguration @SpringJUnitConfig(SecurityBoundaryTest.Config.class)
class SecurityBoundaryTest {
    @Configuration @EnableWebSecurity @org.springframework.web.servlet.config.annotation.EnableWebMvc
    @Import({SecurityConfiguration.class,AuthController.class,ApiExceptionHandler.class,TestEndpoint.class})
    static class Config {
        @Bean AuthDatabaseFixture fixture(){return new AuthDatabaseFixture();}
        @Bean SessionService sessions(AuthDatabaseFixture db){return new SessionService(db.jdbc(),new byte[32],db.clock());}
    }
    @RestController static class TestEndpoint {
        @GetMapping("/api/v1/users/me") public String me(){return "protected";}
        @PostMapping("/api/v1/auth/probe") public String probe(){return "accepted";}
    }
    @Autowired WebApplicationContext context;
    @Test void forgedIdentityDoesNotAuthenticateAndWritesNeedOriginAndCsrf() throws Exception {
        var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/api/v1/users/me").header("X-User-Id","fake").header("X-Role","ADMIN"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        mvc.perform(post("/api/v1/auth/probe").header("Origin","http://127.0.0.1:15173"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        var issued=mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse();
        String token=new com.fasterxml.jackson.databind.ObjectMapper().readTree(issued.getContentAsString()).path("data").path("token").asText();
        var cookie=new jakarta.servlet.http.Cookie("XSRF-TOKEN",token);
        mvc.perform(post("/api/v1/auth/probe").cookie(cookie).header("X-XSRF-TOKEN",token).header("Origin","https://untrusted.example"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/probe").cookie(cookie).header("X-XSRF-TOKEN",token).header("Origin","http://127.0.0.1:15173"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/probe").cookie(cookie).header("X-XSRF-TOKEN",token))
                .andExpect(status().isForbidden());
    }
    @Test void csrfEndpointIssuesTokenAndHttpOnlySessionCookieIsSeparate() throws Exception {
        var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.token").exists())
                .andExpect(header().string("Set-Cookie",org.hamcrest.Matchers.containsString("XSRF-TOKEN=")));
    }
}
