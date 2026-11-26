package com.aistock.notification.email;
import com.aistock.common.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EmailDeliveryControllerTest {
    @Test void internalEndpointRejectsMissingServiceIdentity() throws Exception {
        var controller = new EmailDeliveryController(null, "test-service-token");
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/internal/v1/email-deliveries/" + java.util.UUID.randomUUID()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.data").doesNotExist());
    }
}
