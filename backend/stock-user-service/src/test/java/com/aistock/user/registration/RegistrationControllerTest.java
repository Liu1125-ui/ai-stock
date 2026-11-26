package com.aistock.user.registration;
import com.aistock.common.ApiExceptionHandler;
import com.aistock.user.support.AuthDatabaseFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@Tag("mysql")
class RegistrationControllerTest {
    @Test void acceptedChallengeDoesNotExposeEmailOrVerificationCode() throws Exception {
        var db=new AuthDatabaseFixture(); byte[] key=new byte[32];
        var service=new RegistrationService(db.jdbc(),key,key,db.clock());
        var mvc=MockMvcBuilders.standaloneSetup(new RegistrationController(service)).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(post("/api/v1/auth/registration-challenges").contentType("application/json").content("{\"email\":\"member@example.com\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.challengeId").exists()).andExpect(jsonPath("$.data.email").doesNotExist()).andExpect(jsonPath("$.data.code").doesNotExist());
        mvc.perform(post("/api/v1/auth/registration-challenges").contentType("application/json").content("{\"email\":\"bad-address\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED")).andExpect(jsonPath("$.data").doesNotExist());
    }
}
