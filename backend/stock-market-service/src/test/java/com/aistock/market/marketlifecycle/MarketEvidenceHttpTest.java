package com.aistock.market.marketlifecycle;

import com.aistock.common.*;
import com.aistock.common.marketlifecycle.MarketUseRecords.*;
import com.aistock.market.ingestion.CandidateRequestFilter;
import com.aistock.market.quotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MarketEvidenceHttpTest {
    String id="00000000-0000-0000-0000-000000000001",raw="00000000-0000-0000-0000-000000000002";
    MarketLifecycleService uses;MarketQueryService market;MockMvc mvc;
    @BeforeEach void setup(){uses=mock(MarketLifecycleService.class);market=mock(MarketQueryService.class);mvc=MockMvcBuilders.standaloneSetup(new AnalysisEvidenceController(market)).setControllerAdvice(new ApiExceptionHandler()).addFilters(new CandidateRequestFilter("private",65536),new MarketEvidenceFilter(new MarketEvidenceGate(uses,true),new ObjectMapper())).build();}
    @Test void existingServiceAuthRunsBeforeNewUsageGate()throws Exception{mvc.perform(get("/internal/v1/market/analysis-evidence/SH600030")).andExpect(status().isUnauthorized());verifyNoInteractions(uses,market);}
    @Test void missingCredentialAndWrongPurposeNeverReachEvidenceRead()throws Exception {
        mvc.perform(get("/internal/v1/market/analysis-evidence/SH600030").header("X-Service-Token","private")).andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/internal/v1/market/analysis-evidence/SH600030").header("X-Service-Token","private").header("X-Market-Use-Bundle",id).header("X-Market-Use-Owner","prediction").header("X-Market-Use-Purpose","FORECAST").header("X-Market-Use-Operation",id).header("X-Market-Use-Generation","1")).andExpect(status().isConflict());verifyNoInteractions(uses,market);
    }
    @Test void validUsageReachesEndpointOnceAndScopeIsClearedAfterResponse()throws Exception {
        var identity=new UseIdentity("prediction","STOCK_ANALYSIS",id,"1");var bundle=new MarketLifecycleRecords.Bundle(id,identity,"ACTIVE",List.of(raw),List.of());
        var grant=new MarketLifecycleRecords.Grant(bundle,new Selector("RAW_VERSIONS",List.of(raw),null,null),Map.of("SH600030",List.of(raw)));
        doAnswer(inv->((java.util.function.Function<MarketLifecycleRecords.Grant,Object>)inv.getArgument(2)).apply(grant)).when(uses).withActiveUse(eq(id),eq(identity),any());
        mvc.perform(get("/internal/v1/market/analysis-evidence/SH600030").header("X-Service-Token","private").header("X-Market-Use-Bundle",id).header("X-Market-Use-Owner","prediction").header("X-Market-Use-Purpose","STOCK_ANALYSIS").header("X-Market-Use-Operation",id).header("X-Market-Use-Generation","1").param("from","2026-08-01").param("to","2026-11-03").param("versionId",raw)).andExpect(status().isOk());
        verify(market).daily("SH600030","2026-08-01","2026-11-03","RAW",raw);org.junit.jupiter.api.Assertions.assertNull(MarketEvidenceGate.pinned("SH600030"));
    }
    @Test void allComputationalEvidencePathsAreDeclaredProtected() {
        for(var path:List.of("forecast-raw-window","forecast-window","forecast-inputs","analysis-evidence/SH600030","comparison-raw-window","comparison-evidence","comparison-evidence/"+id,"valuation-quotes","realizations/prepare","realizations/"+id,"alert-evidence/prepare","alert-evidence/versions/"+id,"comparison-refresh"))
            org.junit.jupiter.api.Assertions.assertFalse(MarketEvidenceGate.purposes("/internal/v1/market/"+path).isEmpty(),path);
        org.junit.jupiter.api.Assertions.assertTrue(MarketEvidenceGate.purposes("/internal/v1/market/comparison-options").isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(MarketEvidenceGate.purposes("/internal/v1/market/realizations/operations/"+id).isEmpty());
    }
}
