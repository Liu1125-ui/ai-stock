package com.aistock.market.marketlifecycle;

import com.aistock.common.*;
import com.aistock.common.marketlifecycle.MarketUseRecords.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MarketUseProtocolHttpTest {
    private final String id="00000000-0000-0000-0000-000000000001",version="00000000-0000-0000-0000-000000000002";
    private MarketLifecycleService service;private MarketUseProofClient proofs;private MockMvc mvc;
    private final String base="/internal/v1/market-version-uses";
    private String body(){return "{\"owner\":\"prediction\",\"purpose\":\"FORECAST\",\"ownerOperationId\":\""+id+"\",\"generation\":\"1\",\"requestId\":\""+id+"\",\"selector\":{\"kind\":\"RAW_VERSIONS\",\"versionIds\":[\""+version+"\"]}}";}
    @BeforeEach void setup(){service=mock(MarketLifecycleService.class);proofs=mock(MarketUseProofClient.class);mvc=MockMvcBuilders.standaloneSetup(new MarketUseController(service,proofs,true,false)).setControllerAdvice(new ApiExceptionHandler()).addFilters(new MarketUseAccessFilter("private",new ObjectMapper())).build();}
    @Test void authenticationPrecedesBodyParsingAndAllResponsesAreNoStore()throws Exception {
        mvc.perform(post(base).contentType("application/json").content("bad")).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(post(base).header("X-Service-Token","private","other").contentType("application/json").content(body())).andExpect(status().isUnauthorized());
        verifyNoInteractions(service,proofs);
    }
    @Test void byteLimitUnknownFieldsAndDuplicateKeysNeverReachService()throws Exception {
        mvc.perform(post(base).header("X-Service-Token","private").contentType("application/json").content(new byte[65537])).andExpect(status().isPayloadTooLarge());
        for(var bad:List.of(body().replace("\"generation\":\"1\"","\"generation\":1"),body().replace("\"generation\":\"1\"","\"generation\":\"1\",\"generation\":\"2\""),body()+"{}",body().replace("\"requestId\":","\"extra\":true,\"requestId\":")))
            mvc.perform(post(base).header("X-Service-Token","private").contentType("application/json").content(bad)).andExpect(status().isBadRequest());
        verifyNoInteractions(service,proofs);
    }
    @Test void registerSendsExactlyOneCanonicalRequestAndReturnsProtocol()throws Exception {
        var identity=new UseIdentity("prediction","FORECAST",id,"1");var selector=new Selector("RAW_VERSIONS",List.of(version),null,null);
        var bundle=new MarketLifecycleRecords.Bundle(id,identity,"ACTIVE",List.of(version),List.of(new MarketLifecycleRecords.Use(id,version,"0")));
        when(service.register(id,identity,selector,true)).thenReturn(bundle);
        mvc.perform(post(base).header("X-Service-Token","private").contentType("application/json").content(body())).andExpect(status().isOk()).andExpect(jsonPath("$.data.protocolVersion").value("market-version-use-v1")).andExpect(jsonPath("$.data.bundleId").value(id));
        verify(service).register(id,identity,selector,true);verifyNoMoreInteractions(service);verifyNoInteractions(proofs);
    }
    @Test void callerCannotSupplyItsOwnTerminalProof()throws Exception {
        var bad="{\"owner\":\"prediction\",\"purpose\":\"FORECAST\",\"ownerOperationId\":\""+id+"\",\"generation\":\"1\",\"state\":\"SUCCEEDED\"}";
        mvc.perform(post(base+"/"+id+"/completion").header("X-Service-Token","private").contentType("application/json").content(bad)).andExpect(status().isBadRequest());verifyNoInteractions(service,proofs);
    }
    @Test void capabilitiesAreHonestAndUnknownMethodsAreRejected()throws Exception {
        mvc.perform(get(base+"/capabilities").header("X-Service-Token","private")).andExpect(status().isOk()).andExpect(jsonPath("$.data.enforcementMode").value("OFF")).andExpect(jsonPath("$.data.actionsEnabled").value(false)).andExpect(jsonPath("$.data.evidenceGatesEnabled").value(false));
        mvc.perform(delete(base).header("X-Service-Token","private")).andExpect(status().isNotFound());verifyNoInteractions(service,proofs);
    }
    @Test void registrationLookupRequiresExactIdentityQueryAndNoBody()throws Exception {
        mvc.perform(get(base+"/requests/"+id).header("X-Service-Token","private")).andExpect(status().isBadRequest());
        mvc.perform(get(base+"/requests/"+id).header("X-Service-Token","private").param("owner","prediction").param("purpose","FORECAST").param("ownerOperationId",id).param("generation","1","2")).andExpect(status().isBadRequest());verifyNoInteractions(service,proofs);
    }

    @Test void capabilityReportsEnabledEvidenceGateWithoutClaimingGlobalEnforcement()throws Exception{var enabledMvc=MockMvcBuilders.standaloneSetup(new MarketUseController(service,proofs,true,true)).setControllerAdvice(new ApiExceptionHandler()).addFilters(new MarketUseAccessFilter("private",new ObjectMapper())).build();enabledMvc.perform(get(base+"/capabilities").header("X-Service-Token","private")).andExpect(status().isOk()).andExpect(jsonPath("$.data.evidenceGatesEnabled").value(true)).andExpect(jsonPath("$.data.enforcementMode").value("OFF")).andExpect(jsonPath("$.data.actionsEnabled").value(false));}
}
