package com.aistock.user.security;

import com.aistock.user.account.PasswordHasher;
import com.aistock.user.account.UserRepository;
import com.aistock.user.session.SessionService;
import com.aistock.user.support.AuthDatabaseFixture;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockCookie;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("mysql") @WebAppConfiguration @SpringJUnitConfig(SecurityBoundaryTest.Config.class)
class AuthControllerTest {
    @Autowired WebApplicationContext context;
    @Autowired AuthDatabaseFixture db;
    @Autowired SessionService sessions;
    @Test void loginAndLogoutRotateCsrfAndRevokeCookieSession() throws Exception {
        var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String email="cookie-"+UUID.randomUUID()+"@example.com";
        new UserRepository(db.jdbc()).insertVerifiedOrdinaryUser(UUID.randomUUID(),email,new PasswordHasher().encode("Long-test-password-1"),"Cookie测试",db.clock().instant());
        var csrf=mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse();
        var oldToken=MockCookie.parse(csrf.getHeader("Set-Cookie"));
        var login=mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .cookie(oldToken).header("X-XSRF-TOKEN",oldToken.getValue()).header("Origin","http://127.0.0.1:15173")
                .content("{\"email\":\""+email+"\",\"password\":\"Long-test-password-1\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertEquals(2,login.getHeaders("Set-Cookie").size());
        var session=login.getHeaders("Set-Cookie").stream().map(MockCookie::parse).filter(c->c.getName().equals("AISTOCK_SESSION")).findFirst().orElseThrow();
        assertTrue(session.isHttpOnly());assertEquals("Lax",session.getSameSite());
        assertFalse(login.getContentAsString().contains(session.getValue()));
        sessions.resolve(session.getValue());
        var refreshed=mvc.perform(get("/api/v1/auth/csrf").cookie(session)).andReturn().getResponse();
        var newToken=MockCookie.parse(refreshed.getHeader("Set-Cookie"));
        assertNotEquals(oldToken.getValue(),newToken.getValue());
        var logout=mvc.perform(post("/api/v1/auth/logout").cookie(session,newToken)
                .header("X-XSRF-TOKEN",newToken.getValue()).header("Origin","http://127.0.0.1:15173"))
                .andExpect(status().isOk()).andReturn().getResponse();
        // Security may also clear CSRF on authentication; the final cookie for each name must expire.
        var cleared=new java.util.HashMap<String,MockCookie>();
        logout.getHeaders("Set-Cookie").stream().map(MockCookie::parse).forEach(c->cleared.put(c.getName(),c));
        assertEquals(0,cleared.get("AISTOCK_SESSION").getMaxAge());
        assertEquals(0,cleared.get("XSRF-TOKEN").getMaxAge());
        assertThrows(com.aistock.common.BusinessException.class,()->sessions.resolve(session.getValue()));
        mvc.perform(get("/api/v1/users/me").cookie(session)).andExpect(status().isUnauthorized());
    }
    @Test void malformedLoginBodyReturnsControlledBadRequest() throws Exception {
        var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var issued=mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse();
        var token=MockCookie.parse(issued.getHeader("Set-Cookie"));
        mvc.perform(post("/api/v1/auth/login").cookie(token).header("X-XSRF-TOKEN",token.getValue())
                .header("Origin","http://127.0.0.1:15173").contentType("application/json").content("{broken"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
