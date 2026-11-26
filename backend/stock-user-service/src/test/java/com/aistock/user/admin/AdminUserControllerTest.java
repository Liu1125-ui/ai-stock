package com.aistock.user.admin;
import com.aistock.common.ApiExceptionHandler;
import com.aistock.user.account.*;
import com.aistock.user.security.SecurityConfiguration;
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
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("mysql") @WebAppConfiguration @SpringJUnitConfig(AdminUserControllerTest.Config.class)
class AdminUserControllerTest {
    @Configuration @EnableWebSecurity @org.springframework.web.servlet.config.annotation.EnableWebMvc
    @Import({SecurityConfiguration.class,AuthController.class,ProfileController.class,AdminUserController.class,ApiExceptionHandler.class})
    static class Config {
        @Bean AuthDatabaseFixture db(){return new AuthDatabaseFixture();}
        @Bean SessionService sessions(AuthDatabaseFixture db){return new SessionService(db.jdbc(),new byte[32],db.clock());}
        @Bean ProfileService profiles(AuthDatabaseFixture db){return new ProfileService(db.jdbc(),db.clock());}
        @Bean AdminUserService admins(AuthDatabaseFixture db){return new AdminUserService(db.jdbc(),db.clock());}
    }
    @Autowired WebApplicationContext context;@Autowired AuthDatabaseFixture db;@Autowired SessionService sessions;
    @Test void ownerComesFromCookieAndOrdinaryUserCannotManageAccounts() throws Exception {
        var member=new UserRepository(db.jdbc()).findByEmail("member@example.com").map(UserRepository.Account::userId).orElseGet(()->AdminUserServiceTest.member(db));
        var session=sessions.login("member@example.com","Long-test-password-1","test");
        var cookie=new MockCookie("AISTOCK_SESSION",session.rawToken());
        var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/api/v1/users/me").cookie(cookie).header("X-User-Id","another").header("X-Role","ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value(member.toString())).andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        mvc.perform(get("/api/v1/admin/users").cookie(cookie)).andExpect(status().isForbidden());
    }
    @Test void administratorEditsWithRevisionAndRejectsNullGrantOrRoleInjection() throws Exception {
        // Context fixture persists within this class; use existing member when the other test ran first.
        var users=new UserRepository(db.jdbc());var member=users.findByEmail("member@example.com").map(UserRepository.Account::userId).orElseGet(()->AdminUserServiceTest.member(db));
        var admin=AdminUserServiceTest.admin(db);
        var cookie=new MockCookie("AISTOCK_SESSION",sessions.login("admin@example.com","Long-admin-password-1","admin").rawToken());
        var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var issued=mvc.perform(get("/api/v1/auth/csrf").cookie(cookie)).andReturn().getResponse();var csrf=MockCookie.parse(issued.getHeader("Set-Cookie"));
        mvc.perform(put("/api/v1/admin/users/"+member+"/experiment-grant").cookie(cookie,csrf).header("X-XSRF-TOKEN",csrf.getValue())
                .header("Origin","http://127.0.0.1:15173").contentType("application/json").content("{\"revision\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/admin/users/"+member+"/status").cookie(cookie,csrf).header("X-XSRF-TOKEN",csrf.getValue())
                .header("Origin","http://127.0.0.1:15173").contentType("application/json").content("{\"status\":\"DISABLED\",\"revision\":0,\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/users").cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
    }
}
