package com.aistock.market.marketlifecycle;

import com.aistock.common.BusinessException;
import com.aistock.common.marketlifecycle.MarketUseRecords.*;
import com.aistock.market.support.MarketDatabaseFixture;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("mysql")
class MarketUseProtocolMysqlTest {
    private JdbcTemplate jdbc;private MarketLifecycleService service;private String raw;
    private final Clock clock=Clock.fixed(Instant.parse("2026-11-04T08:00:00Z"),ZoneOffset.UTC);
    @BeforeEach void setup() {
        jdbc=new MarketDatabaseFixture().jdbc();raw=UUID.randomUUID().toString();
        jdbc.update("insert into instrument(instrument_id,ticker,display_name,demo_only) values('SH600030','SH600030','test',false)");
        jdbc.update("insert into market_data_version(version_id,instrument_id,provider,price_basis,status,quality,content_hash,source_payload_hash,fetched_at,published_at,coverage_start,coverage_end) values(?,'SH600030','baostock','RAW','AVAILABLE','ROW_VALIDATED',?,?,now(3),now(3),'2026-01-01','2026-11-03')",raw,"a".repeat(64),"b".repeat(64));
        jdbc.update("insert into market_version_lifecycle(version_id,use_state,revision,updated_at) values(?,'ENABLED',0,now(3))",raw);
        jdbc.update("insert into instrument_data_head(instrument_id,provider,price_basis,version_id,revision) values('SH600030','baostock','RAW',?,1)",raw);
        service=new MarketLifecycleService(jdbc,clock,()->false);
    }
    private UseIdentity identity(){return new UseIdentity("prediction","FORECAST",UUID.randomUUID().toString(),"1");}
    private Selector selector(){return new Selector("RAW_VERSIONS",List.of(raw),null,null);}
    @Test void requestReceiptAndSelectorSurviveReplayAndClosedProtocol() {
        var request=UUID.randomUUID().toString();var identity=identity();var b=service.register(request,identity,selector(),true);
        assertEquals(b.bundleId(),service.register(request,identity,selector(),false).bundleId());
        assertEquals(selector(),service.selector(b.bundleId(),identity));assertEquals(b.bundleId(),service.registration(request,identity).bundleId());
        assertEquals(1,jdbc.queryForObject("select count(*) from market_version_use_request",Integer.class));
        assertThrows(BusinessException.class,()->service.register(request,identity(),selector(),true));
        assertThrows(BusinessException.class,()->service.register(UUID.randomUUID().toString(),identity(),selector(),false));
        assertThrows(BusinessException.class,()->service.registration(request,identity()));
    }
    @Test void releasedOriginalRequestReplaysReleasedReceiptWithoutNewUse() {
        var request=UUID.randomUUID().toString();var id=identity();var b=service.register(request,id,selector(),true);
        service.releaseVerified(b.bundleId(),new MarketLifecycleRecords.TerminalProof(id,"SUCCEEDED",clock.instant(),"1",id.ownerOperationId(),b.versionIds()));
        assertEquals("RELEASED",service.register(request,id,selector(),false).state());
        assertEquals(1,jdbc.queryForObject("select count(*) from market_version_use",Integer.class));
        assertThrows(BusinessException.class,()->service.register(UUID.randomUUID().toString(),id,selector(),true));
    }
    @Test void existingLegacySelectorCanOnlyBeRestoredFromMatchingCanonicalRequest() {
        var id=identity();var b=service.acquire(id,selector());jdbc.update("update market_version_use_bundle set selector_json=null where bundle_id=?",b.bundleId());
        assertThrows(BusinessException.class,()->service.selector(b.bundleId(),id));
        service.register(UUID.randomUUID().toString(),id,selector(),true);assertEquals(selector(),service.selector(b.bundleId(),id));
    }
    @Test void unknownAndMalformedProofsRotateWithoutReleasingOrStarvingLaterWork() {
        for(int i=0;i<52;i++)service.register(UUID.randomUUID().toString(),identity(),selector(),true);
        var pending=service.pendingUseIds(50);var corrupt=pending.get(0);jdbc.update("update market_version_use_bundle set versions_hash=? where bundle_id=?","0".repeat(64),corrupt);
        var proofs=mock(MarketUseProofClient.class);when(proofs.read(any())).thenReturn(Optional.empty());
        var reconciler=new MarketUseReconciler(service,proofs,true);var first=reconciler.runOnce();assertEquals(50,first.checked());assertEquals(0,first.released());assertEquals(50,first.retained());
        var untouched=jdbc.queryForList("select bundle_id from market_version_use_bundle where last_checked_at is null",String.class);assertEquals(2,untouched.size());
        reconciler.runOnce();assertEquals(0,jdbc.queryForObject("select count(*) from market_version_use_bundle where last_checked_at is null",Integer.class));
        assertEquals(52,jdbc.queryForObject("select count(*) from market_version_use where state='ACTIVE'",Integer.class));
        for(var id:untouched)verify(proofs).read(org.mockito.ArgumentMatchers.argThat(b->id.equals(b.bundleId())));
    }
    @Test void validOwnerProofAllowsReconciliationAndDisabledWorkerDoesNothing() {
        var b=service.register(UUID.randomUUID().toString(),identity(),selector(),true);var proofs=mock(MarketUseProofClient.class);
        when(proofs.read(any())).thenReturn(Optional.of(new MarketLifecycleRecords.TerminalProof(b.identity(),"SUCCEEDED",clock.instant(),"1",b.identity().ownerOperationId(),b.versionIds())));
        assertEquals(new MarketUseReconciler.Result(0,0,0),new MarketUseReconciler(service,proofs,false).runOnce());verifyNoInteractions(proofs);
        assertEquals(1,new MarketUseReconciler(service,proofs,true).runOnce().released());assertEquals("RELEASED",service.binding(b.bundleId(),b.identity()).state());
    }
}
