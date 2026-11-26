package com.aistock.market.refresh;

import com.aistock.common.ApiExceptionHandler;
import com.aistock.common.BusinessException;
import com.aistock.common.adminrefresh.AdminMarketRefreshRecords.Context;
import com.aistock.market.ingestion.CandidateRequestFilter;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminRefreshContextCacheTest {
    private static final String BASE="/internal/v1/market/admin-refresh-context";
    private MockMvc mvc(String token) {
        var repository=mock(AdminRefreshContextRepository.class);
        when(repository.find(eq("SH600030"),any())).thenReturn(new Context("SH600030","baostock","RAW",false,"REAL_MARKET_VERSION_REQUIRED",null,null,null,null,Instant.parse("2026-10-27T04:00:00Z")));
        when(repository.find(eq("SH999999"),any())).thenThrow(new BusinessException("MARKET_ADMIN_RESOURCE_NOT_FOUND","not found",HttpStatus.NOT_FOUND));
        when(repository.find(eq("SZ000002"),any())).thenThrow(new BusinessException("MARKET_ADMIN_UNAVAILABLE","unavailable",HttpStatus.SERVICE_UNAVAILABLE));
        return MockMvcBuilders.standaloneSetup(new AdminRefreshContextController(repository,Clock.fixed(Instant.parse("2026-10-27T04:00:00Z"),ZoneOffset.UTC)))
            .setControllerAdvice(new ApiExceptionHandler()).addFilters(new CandidateRequestFilter(token,4096)).build();
    }
    @Test void parameterAndBodyErrorsCannotBeCached() throws Exception {
        var mvc=mvc("fixture-only");
        mvc.perform(get(BASE+"/SH600030?unexpected=1").header("X-Service-Token","fixture-only"))
            .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get(BASE+"/SH600030").header("X-Service-Token","fixture-only").content("x"))
            .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void missingResourceAndUnavailableDependencyCannotBeCached() throws Exception {
        var mvc=mvc("fixture-only");
        mvc.perform(get(BASE+"/SH999999").header("X-Service-Token","fixture-only"))
            .andExpect(status().isNotFound()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get(BASE+"/SZ000002").header("X-Service-Token","fixture-only"))
            .andExpect(status().isServiceUnavailable()).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void successfulAndUnauthenticatedResponsesRetainNoStore() throws Exception {
        var mvc=mvc("fixture-only");
        mvc.perform(get(BASE+"/SH600030").header("X-Service-Token","fixture-only"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get(BASE+"/SH600030"))
            .andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","no-store"));
        mvc("").perform(get(BASE+"/SH600030"))
            .andExpect(status().isServiceUnavailable()).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void unmatchedPathInResourceSubtreeCannotBeCached() throws Exception {
        mvc("fixture-only").perform(get(BASE+"/SH600030/extra").header("X-Service-Token","fixture-only"))
            .andExpect(status().isNotFound()).andExpect(header().string("Cache-Control","no-store"));
    }
}