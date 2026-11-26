package com.aistock.market.marketlifecycle;

import com.aistock.common.ApiExceptionHandler;
import com.aistock.common.ApiResponse;
import com.aistock.market.adminquery.AdminMarketDataAccessFilter;
import com.aistock.market.security.SessionIdentityClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MarketLifecycleAdminReadHttpTest {
    private static final String STOCK="SH600030", VERSION="11111111-1111-1111-1111-111111111111";
    private static final String PATH="/api/v1/admin/market-data/"+STOCK+"/versions/"+VERSION+"/lifecycle";
    private final Cookie session=new Cookie("AISTOCK_SESSION","fixture-session");
    private final SessionIdentityClient identity=mock(SessionIdentityClient.class);
    private final MarketLifecycleService lifecycle=mock(MarketLifecycleService.class);
    private MockMvc mvc;

    @BeforeEach void setup() {
        role("ADMIN");
        when(lifecycle.snapshot(STOCK,VERSION)).thenReturn(new MarketLifecycleRecords.Snapshot(STOCK,VERSION,"DRAINING","2",VERSION,1,false));
        when(lifecycle.history(STOCK,VERSION,null,20)).thenReturn(new MarketLifecycleRecords.History(List.of(),null));
        mvc=MockMvcBuilders.standaloneSetup(new MarketLifecycleAdminReadController(lifecycle))
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new AdminMarketDataAccessFilter(identity,"fixture-token",new ObjectMapper())).build();
    }

    @Test void adminCanReadStateAndHistoryWithoutEnablingCommands() throws Exception {
        mvc.perform(get(PATH).cookie(session)).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.data.useState").value("DRAINING"))
            .andExpect(jsonPath("$.data.activeUses").value(1))
            .andExpect(jsonPath("$.data.actionsReady").value(false));
        mvc.perform(get(PATH+"/history").cookie(session)).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.data.items").isEmpty());
        verify(lifecycle).snapshot(STOCK,VERSION);
        verify(lifecycle).history(STOCK,VERSION,null,20);
    }

    @Test void unauthorizedAndMalformedRequestsNeverAccessLifecycleData() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        role("USER");
        mvc.perform(get(PATH+"/history").cookie(session)).andExpect(status().isForbidden());
        role("ADMIN");
        mvc.perform(get(PATH).cookie(session).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(get(PATH+"/history").cookie(session).param("limit","1","2")).andExpect(status().isBadRequest());
        mvc.perform(get(PATH+"/history").cookie(session).param("limit","21")).andExpect(status().isBadRequest());
        mvc.perform(get(PATH+"/history").cookie(session).param("actor","spoof")).andExpect(status().isBadRequest());
        mvc.perform(post(PATH).cookie(session)).andExpect(status().isNotFound());
        verifyNoInteractions(lifecycle);
    }

    private void role(String role) {
        when(identity.resolve(any(),anyString())).thenReturn(ApiResponse.success(
            new SessionIdentityClient.Identity("00000000-0000-0000-0000-000000000001",role,false,1),"trace"));
    }
}
