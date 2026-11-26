package com.aistock.market.refresh;

import com.aistock.common.ApiExceptionHandler;
import com.aistock.market.ingestion.CandidateRequestFilter;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("mysql")
class AdminRefreshConstraintHttpTest {
    @ParameterizedTest
    @ValueSource(strings={"1.9","\"1\"","1e0","true","-1","null","9223372036854775808"})
    void rejectsCoercedRevisionBeforeReceiptOrCollection(String value) throws Exception {
        exercise(value,400,0);
    }
    @Test void acceptsExplicitIntegerRevisionAndKeepsCollectorFailureReceipt() throws Exception {
        exercise("1",200,1);
    }
    private void exercise(String revision,int expectedStatus,int expectedCalls) throws Exception {
        var f=new AdminRefreshFixture();String raw=f.seed();var calls=new AtomicInteger();
        var service=f.service((operation,id,start,end)->{calls.incrementAndGet();return new MarketCollector.Collected(operation,"SOURCE_TIMEOUT",null,"SOURCE_TIMEOUT");});
        var mvc=MockMvcBuilders.standaloneSetup(new RefreshController(service,new RefreshRepository(f.jdbc)))
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new CandidateRequestFilter("fixture-internal",4096)).build();
        String json="{\"operationId\":\"strict-http\",\"instrumentId\":\"SH600030\",\"targetDate\":\"2026-10-24\",\"leaseUntil\":\"2026-10-25T12:01:30Z\",\"adminConstraint\":{\"baseVersionId\":\""+raw+"\",\"headRevision\":"+revision+",\"coverageStart\":\"2026-10-23\",\"policyVersion\":\"admin-market-refresh-v1\"}}";
        mvc.perform(post("/internal/v1/market/refresh").header("X-Service-Token","fixture-internal")
            .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().is(expectedStatus));
        assertEquals(expectedCalls,calls.get());
        assertEquals(expectedCalls,f.jdbc.queryForObject("select count(*) from market_refresh_receipt",Integer.class));
    }
}