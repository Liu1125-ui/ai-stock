package com.aistock.market.marketlifecycle;

import com.aistock.common.BusinessException;
import com.aistock.common.marketlifecycle.MarketUseRecords.*;
import com.aistock.market.support.MarketDatabaseFixture;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("mysql")
class MarketLifecycleTransactionsMysqlTest {
    private JdbcTemplate jdbc;
    private MarketLifecycleService service;
    private final Clock clock=Clock.fixed(Instant.parse("2026-11-04T06:00:00Z"),ZoneOffset.UTC);
    private final String actor="00000000-0000-0000-0000-000000000001";
    private String old,current;
    @BeforeEach void setup() {
        jdbc=new MarketDatabaseFixture().jdbc();
        jdbc.update("insert into instrument(instrument_id,ticker,display_name,demo_only) values('SH600030','SH600030','test',false)");
        old=version();current=version();
        jdbc.update("insert into instrument_data_head(instrument_id,provider,price_basis,version_id,revision) values('SH600030','baostock','RAW',?,1)",current);
        service=new MarketLifecycleService(jdbc,clock,()->true);
    }
    private String version() {
        var id=UUID.randomUUID().toString();var hash=id.replace("-","")+id.replace("-","");
        jdbc.update("insert into market_data_version(version_id,instrument_id,provider,price_basis,status,quality,content_hash,source_payload_hash,fetched_at,published_at,coverage_start,coverage_end) values(?,'SH600030','baostock','RAW','AVAILABLE','ROW_VALIDATED',?,?,now(3),now(3),'2026-01-01','2026-11-03')",id,hash,"b".repeat(64));
        jdbc.update("insert into market_version_lifecycle(version_id,use_state,revision,updated_at) values(?,'ENABLED',0,now(3))",id);return id;
    }
    private UseIdentity identity(){return new UseIdentity("prediction","FORECAST",UUID.randomUUID().toString(),"1");}
    private Selector raw(String... versions){return new Selector("RAW_VERSIONS",List.of(versions),null,null);}
    private Command disable(String rev){return new Command(UUID.randomUUID().toString(),"DISABLE",rev,"隔离测试停用");}
    private Command restore(String rev){return new Command(UUID.randomUUID().toString(),"RESTORE",rev,"隔离测试恢复");}
    private MarketLifecycleRecords.TerminalProof proof(MarketLifecycleRecords.Bundle bundle) {
        return new MarketLifecycleRecords.TerminalProof(bundle.identity(),"SUCCEEDED",clock.instant(),"1",bundle.identity().ownerOperationId(),bundle.versionIds());
    }
    private int count(String table){return jdbc.queryForObject("select count(*) from "+table,Integer.class);}
    private void await(CountDownLatch latch){try{assertTrue(latch.await(5,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
    private <T> T held(java.util.function.Supplier<T> action,CountDownLatch ready,CountDownLatch release) {
        var tx=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);return tx.execute(s->{var result=action.get();ready.countDown();await(release);return result;});
    }
    @Test void acquireThenDisableWaitsAndLastVerifiedReleaseCompletesAction() throws Exception {
        var id=identity();var ready=new CountDownLatch(1);var release=new CountDownLatch(1);var started=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var acquire=pool.submit(()->held(()->service.acquire(id,raw(old)),ready,release));await(ready);
            var command=disable("0");var stop=pool.submit(()->{started.countDown();return service.action("SH600030",old,actor,command);});await(started);
            assertThrows(TimeoutException.class,()->stop.get(200,TimeUnit.MILLISECONDS));release.countDown();
            var bundle=acquire.get(10,TimeUnit.SECONDS);assertEquals("WAITING",stop.get(10,TimeUnit.SECONDS).state());
            assertEquals("DRAINING",service.snapshot("SH600030",old).useState());
            assertEquals("MARKET_VERSION_USE_BLOCKED",assertThrows(BusinessException.class,()->service.acquire(identity(),raw(old))).code());
            assertEquals(bundle.bundleId(),service.acquire(id,raw(old)).bundleId());
            service.releaseVerified(bundle.bundleId(),proof(bundle));
            assertEquals("DISABLED",service.snapshot("SH600030",old).useState());
            assertEquals("COMPLETED",service.operation("SH600030",old,actor,command.operationId()).state());
            assertEquals("RELEASED",service.binding(bundle.bundleId(),id).state());
            service.releaseVerified(bundle.bundleId(),proof(bundle));
            assertEquals(2,count("market_version_action_event"));
        } finally {release.countDown();pool.shutdownNow();}
    }
    @Test void disableThenAcquireRejectsWithoutCreatingPartialBundle() throws Exception {
        var ready=new CountDownLatch(1);var release=new CountDownLatch(1);var started=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var stop=pool.submit(()->held(()->service.action("SH600030",old,actor,disable("0")),ready,release));await(ready);
            var acquire=pool.submit(()->{started.countDown();return assertThrows(BusinessException.class,()->service.acquire(identity(),raw(old))).code();});await(started);
            assertThrows(TimeoutException.class,()->acquire.get(200,TimeUnit.MILLISECONDS));release.countDown();
            assertEquals("COMPLETED",stop.get(10,TimeUnit.SECONDS).state());
            assertEquals("MARKET_VERSION_USE_BLOCKED",acquire.get(10,TimeUnit.SECONDS));
            assertEquals(0,count("market_version_use_bundle"));assertEquals(0,count("market_version_use"));
        } finally {release.countDown();pool.shutdownNow();}
    }
    @Test void multiVersionRegistrationIsAtomicAndCannotChangeSameGeneration() {
        var second=version();service.action("SH600030",second,actor,disable("0"));
        var id=new UseIdentity("market","COMPARISON_EVIDENCE_PUBLISH",UUID.randomUUID().toString(),"1");
        assertThrows(BusinessException.class,()->service.acquire(id,raw(old,second)));
        assertEquals(0,count("market_version_use_bundle"));assertEquals(0,count("market_version_use"));
        var bundle=service.acquire(identity(),raw(old));
        assertEquals("MARKET_USE_OPERATION_CONFLICT",assertThrows(BusinessException.class,()->service.acquire(bundle.identity(),raw(current))).code());
        assertEquals(1,count("market_version_use"));
    }
    @Test void fixedComparisonEvidenceAndHeadRetriesNeverRebind() {
        var evidence1=UUID.randomUUID().toString();var evidence2=UUID.randomUUID().toString();
        jdbc.update("insert into instrument(instrument_id,ticker,display_name,demo_only) values('SH600276','SH600276','test',false)");
        var other=version();jdbc.update("update market_data_version set instrument_id='SH600276' where version_id=?",other);
        jdbc.update("insert into instrument_data_head(instrument_id,provider,price_basis,version_id,revision) values('SH600276','baostock','RAW',?,1)",other);
        for(var pair:List.of(List.of(evidence1,"SH600030",old,"c"),List.of(evidence2,"SH600276",other,"d")))
            jdbc.update("insert into comparison_evidence_version(evidence_id,instrument_id,raw_version_id,content_hash,evidence_json,created_at) values(?,?,?,?,?,now(3))",pair.get(0),pair.get(1),pair.get(2),pair.get(3).repeat(64),"{}");
        var compare=new UseIdentity("prediction","COMPARISON",UUID.randomUUID().toString(),"1");
        var selected=service.acquire(compare,new Selector("COMPARISON_EVIDENCE",null,List.of(new EvidenceBinding("SH600030",evidence1),new EvidenceBinding("SH600276",evidence2)),null));
        assertEquals(Set.of(old,other),new HashSet<>(selected.versionIds()));assertFalse(selected.versionIds().contains(current));
        var id=identity();var selector=new Selector("CURRENT_HEADS",null,null,List.of("SH600030"));var first=service.acquire(id,selector);
        jdbc.update("update instrument_data_head set version_id=?,revision=revision+1 where instrument_id='SH600030'",old);
        assertEquals(first.bundleId(),service.acquire(id,selector).bundleId());assertEquals(List.of(current),service.acquire(id,selector).versionIds());
    }
    @Test void restoreSupersedesOldDrainAndSecondDrainUsesNewCycle() {
        var b=service.acquire(identity(),raw(old));var first=disable("0");service.action("SH600030",old,actor,first);
        service.action("SH600030",old,actor,restore("1"));
        assertEquals("SUPERSEDED",service.operation("SH600030",old,actor,first.operationId()).state());
        var second=disable("2");service.action("SH600030",old,actor,second);service.releaseVerified(b.bundleId(),proof(b));
        var currentState=service.snapshot("SH600030",old);assertEquals("DISABLED",currentState.useState());assertEquals(second.operationId(),currentState.cycleId());
        assertEquals("COMPLETED",service.operation("SH600030",old,actor,second.operationId()).state());
        assertEquals("SUPERSEDED",service.operation("SH600030",old,actor,first.operationId()).state());
        assertEquals(current,jdbc.queryForObject("select version_id from instrument_data_head where instrument_id='SH600030'",String.class));
    }
    @Test void unknownOrMismatchedProofNeverReleasesAndReleasedUseCannotBeReused() {
        var b=service.acquire(identity(),raw(old));var p=proof(b);
        for(var bad:List.of(new MarketLifecycleRecords.TerminalProof(p.identity(),"UNKNOWN",p.terminalAt(),"1",p.resultIdentity(),p.versionIds()),
                new MarketLifecycleRecords.TerminalProof(new UseIdentity("prediction","FORECAST",p.identity().ownerOperationId(),"2"),"SUCCEEDED",p.terminalAt(),"1",p.resultIdentity(),p.versionIds()),
                new MarketLifecycleRecords.TerminalProof(p.identity(),"SUCCEEDED",p.terminalAt(),"1",p.resultIdentity(),List.of(current))))
            assertThrows(BusinessException.class,()->service.releaseVerified(b.bundleId(),bad));
        assertEquals("ACTIVE",service.binding(b.bundleId(),b.identity()).state());service.releaseVerified(b.bundleId(),p);
        assertEquals("MARKET_USE_ALREADY_RELEASED",assertThrows(BusinessException.class,()->service.acquire(b.identity(),raw(old))).code());
    }
    @Test void replayWorksWithGateClosedWhileNewCommandsAndBadOwnersAreRejected() {
        var c=disable("0");var first=service.action("SH600030",old,actor,c);var closed=new MarketLifecycleService(jdbc,clock,()->false);
        assertEquals(first,closed.action("SH600030",old,actor,c));
        assertEquals("MARKET_LIFECYCLE_NOT_READY",assertThrows(BusinessException.class,()->closed.action("SH600030",old,actor,restore("1"))).code());
        assertEquals("MARKET_VERSION_OPERATION_CONFLICT",assertThrows(BusinessException.class,()->service.action("SH600030",old,UUID.randomUUID().toString(),c)).code());
        assertEquals("MARKET_VERSION_OPERATION_CONFLICT",assertThrows(BusinessException.class,()->service.action("SH600030",old,actor,new Command(c.operationId(),"RESTORE","0",c.reason()))).code());
        assertEquals("REJECTED",service.action("SH600030",current,actor,disable("0")).state());
        assertEquals("MARKET_VERSION_CURRENT_HEAD",service.history("SH600030",current,null,20).items().get(0).errorCode());
    }
    @Test void staleRevisionHeadChangesAndMissingLifecycleAreFailClosed() {
        assertEquals("MARKET_VERSION_REVISION_CONFLICT",service.action("SH600030",old,actor,disable("9")).errorCode());
        jdbc.update("update instrument_data_head set version_id=? where instrument_id='SH600030'",old);
        assertEquals("MARKET_VERSION_CURRENT_HEAD",service.action("SH600030",old,actor,disable("0")).errorCode());
        jdbc.update("delete from market_version_lifecycle where version_id=?",current);
        assertEquals("MARKET_LIFECYCLE_NOT_READY",assertThrows(BusinessException.class,()->service.acquire(identity(),raw(current))).code());
    }
    @Test void sqlFailureRollsBackStateActionAndAuditTogether() {
        jdbc.execute("create trigger fail_lifecycle_event before insert on market_version_action_event for each row signal sqlstate '45000' set message_text='injected test failure'");
        try {assertThrows(BusinessException.class,()->service.action("SH600030",old,actor,disable("0")));}
        finally {jdbc.execute("drop trigger fail_lifecycle_event");}
        assertEquals("ENABLED",service.snapshot("SH600030",old).useState());assertEquals("0",service.snapshot("SH600030",old).revision());
        assertEquals(0,count("market_version_action"));assertEquals(0,count("market_version_action_event"));
    }
    @Test void replayAndWholeBundleReleaseUseSameLockOrder() throws Exception {
        for(boolean replayFirst:List.of(true,false)) {
            var b=service.acquire(identity(),raw(old));var ready=new CountDownLatch(1);var release=new CountDownLatch(1);var started=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
            try {
                var first=pool.submit(()->held(()->{if(replayFirst)service.acquire(b.identity(),raw(old));else service.releaseVerified(b.bundleId(),proof(b));return true;},ready,release));await(ready);
                var second=pool.submit(()->{started.countDown();if(replayFirst)service.releaseVerified(b.bundleId(),proof(b));else assertEquals("MARKET_USE_ALREADY_RELEASED",assertThrows(BusinessException.class,()->service.acquire(b.identity(),raw(old))).code());return true;});await(started);
                assertThrows(TimeoutException.class,()->second.get(200,TimeUnit.MILLISECONDS));release.countDown();assertTrue(first.get(10,TimeUnit.SECONDS));assertTrue(second.get(10,TimeUnit.SECONDS));
                assertEquals("RELEASED",service.binding(b.bundleId(),b.identity()).state());
            }finally{release.countDown();pool.shutdownNow();}
        }
    }
    @Test void sameOwnerOperationGenerationCannotChangePurpose() {
        var b=service.acquire(identity(),raw(old));var original=b.identity();
        var altered=new UseIdentity(original.owner(),"STOCK_ANALYSIS",original.ownerOperationId(),original.generation());
        assertEquals("MARKET_USE_OPERATION_CONFLICT",assertThrows(BusinessException.class,()->service.acquire(altered,raw(old))).code());
        assertEquals(1,count("market_version_use_bundle"));
    }
    @Test void wholeBundleReleaseRollsBackWhenAnyDrainCompletionFails() {
        var other=version();var id=new UseIdentity("market","COMPARISON_EVIDENCE_PUBLISH",UUID.randomUUID().toString(),"1");
        var b=service.acquire(id,raw(old,other));service.action("SH600030",old,actor,disable("0"));service.action("SH600030",other,actor,disable("0"));
        jdbc.execute("create trigger fail_lifecycle_release before insert on market_version_action_event for each row begin if new.event_type='COMPLETED' then signal sqlstate '45000' set message_text='injected release failure'; end if; end");
        try {assertThrows(BusinessException.class,()->service.releaseVerified(b.bundleId(),proof(b)));}
        finally {jdbc.execute("drop trigger fail_lifecycle_release");}
        assertEquals("ACTIVE",service.binding(b.bundleId(),id).state());
        assertEquals(2,jdbc.queryForObject("select count(*) from market_version_use where state='ACTIVE'",Integer.class));
        assertEquals("DRAINING",service.snapshot("SH600030",old).useState());assertEquals("DRAINING",service.snapshot("SH600030",other).useState());
        service.releaseVerified(b.bundleId(),proof(b));assertEquals("DISABLED",service.snapshot("SH600030",old).useState());assertEquals("DISABLED",service.snapshot("SH600030",other).useState());
    }
    @Test void historyHasStableCursorAndNewGenerationGetsNewBinding() {
        var b=service.acquire(identity(),raw(old));var original=b.identity();
        var next=service.acquire(new UseIdentity(original.owner(),original.purpose(),original.ownerOperationId(),"2"),raw(old));
        assertNotEquals(b.bundleId(),next.bundleId());
        var c=disable("0");service.action("SH600030",old,actor,c);
        service.action("SH600030",old,actor,restore("1"));service.action("SH600030",old,actor,disable("2"));
        var first=service.history("SH600030",old,null,2);assertEquals(2,first.items().size());assertNotNull(first.nextCursor());
        var second=service.history("SH600030",old,first.nextCursor(),2);assertEquals(1,second.items().size());assertNull(second.nextCursor());
        var ops=new HashSet<String>();first.items().forEach(r->assertTrue(ops.add(r.operationId())));second.items().forEach(r->assertTrue(ops.add(r.operationId())));
    }
    @Test void twoInitialAcquiresReturnExactlyOnePersistedBundle() throws Exception {
        var id=identity();var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->{await(start);return service.acquire(id,raw(old));});
            var b=pool.submit(()->{await(start);return service.acquire(id,raw(old));});start.countDown();
            assertEquals(a.get(10,TimeUnit.SECONDS).bundleId(),b.get(10,TimeUnit.SECONDS).bundleId());
            assertEquals(1,count("market_version_use_bundle"));assertEquals(1,count("market_version_use"));
        }finally{pool.shutdownNow();}
    }
    @Test void restoreAndLastReleaseSerializeWithRevisionConflictInsteadOfLostUpdate() throws Exception {
        for(boolean restoreFirst:List.of(true,false)) {
            if(!restoreFirst)setup();
            var b=service.acquire(identity(),raw(old));var firstStop=disable("0");service.action("SH600030",old,actor,firstStop);
            var ready=new CountDownLatch(1);var release=new CountDownLatch(1);var started=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
            try {
                var restore=restore("1");
                var first=pool.submit(()->held(()->{if(restoreFirst)service.action("SH600030",old,actor,restore);else service.releaseVerified(b.bundleId(),proof(b));return true;},ready,release));await(ready);
                var second=pool.submit(()->{started.countDown();if(restoreFirst){service.releaseVerified(b.bundleId(),proof(b));return "RELEASED";}return service.action("SH600030",old,actor,restore).errorCode();});await(started);
                assertThrows(TimeoutException.class,()->second.get(200,TimeUnit.MILLISECONDS));release.countDown();assertTrue(first.get(10,TimeUnit.SECONDS));
                if(restoreFirst){assertEquals("RELEASED",second.get(10,TimeUnit.SECONDS));assertEquals("ENABLED",service.snapshot("SH600030",old).useState());assertEquals("SUPERSEDED",service.operation("SH600030",old,actor,firstStop.operationId()).state());}
                else{assertEquals("MARKET_VERSION_REVISION_CONFLICT",second.get(10,TimeUnit.SECONDS));assertEquals("DISABLED",service.snapshot("SH600030",old).useState());service.action("SH600030",old,actor,restore("2"));assertEquals("ENABLED",service.snapshot("SH600030",old).useState());}
            }finally{release.countDown();pool.shutdownNow();}
        }
    }
    @Test void restoreReleaseAndSecondDisableRaceCannotCompleteWrongCycle() throws Exception {
        var b=service.acquire(identity(),raw(old));var firstStop=disable("0");service.action("SH600030",old,actor,firstStop);
        var ready=new CountDownLatch(1);var release=new CountDownLatch(1);var entered=new CountDownLatch(2);var pool=Executors.newFixedThreadPool(3);
        try {
            var restored=pool.submit(()->held(()->service.action("SH600030",old,actor,restore("1")),ready,release));await(ready);
            var finished=pool.submit(()->{entered.countDown();service.releaseVerified(b.bundleId(),proof(b));return true;});
            var next=disable("2");var disabled=pool.submit(()->{entered.countDown();return service.action("SH600030",old,actor,next);});await(entered);
            assertThrows(TimeoutException.class,()->disabled.get(200,TimeUnit.MILLISECONDS));assertThrows(TimeoutException.class,()->finished.get(200,TimeUnit.MILLISECONDS));release.countDown();
            assertEquals("COMPLETED",restored.get(10,TimeUnit.SECONDS).state());assertTrue(finished.get(10,TimeUnit.SECONDS));disabled.get(10,TimeUnit.SECONDS);
            var state=service.snapshot("SH600030",old);assertEquals("DISABLED",state.useState());assertEquals(next.operationId(),state.cycleId());
            assertEquals("SUPERSEDED",service.operation("SH600030",old,actor,firstStop.operationId()).state());assertEquals("COMPLETED",service.operation("SH600030",old,actor,next.operationId()).state());
        }finally{release.countDown();pool.shutdownNow();}
    }

    @Test void adminHttpDisableWaitsAndRestoreReopensWithoutMovingHead() throws Exception {
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var identity=org.mockito.Mockito.mock(com.aistock.market.security.SessionIdentityClient.class);
        org.mockito.Mockito.when(identity.resolve(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(com.aistock.common.ApiResponse.success(
                new com.aistock.market.security.SessionIdentityClient.Identity(actor,"ADMIN",false,1),"test"));
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders
            .standaloneSetup(new MarketLifecycleAdminReadController(service))
            .setControllerAdvice(new com.aistock.common.ApiExceptionHandler())
            .addFilters(new com.aistock.market.adminquery.AdminMarketDataAccessFilter(identity,"fixture",mapper)).build();
        var cookie=new jakarta.servlet.http.Cookie("AISTOCK_SESSION","fixture");
        String path="/api/v1/admin/market-data/SH600030/versions/"+old+"/lifecycle";
        var bundle=service.acquire(identity(),raw(old));
        var stop=disable("0");
        var body=mapper.writeValueAsBytes(stop);
        mvc.perform(post(path+"/actions").cookie(cookie).contentType("application/json").content(body))
            .andExpect(status().isForbidden());
        assertEquals(0,count("market_version_action"));
        mvc.perform(post(path+"/actions").cookie(cookie).contentType("application/json").content(body)
            .header("Origin","http://127.0.0.1:15173").header("X-Aistock-Admin-Action","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("WAITING"));
        mvc.perform(get(path).cookie(cookie)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.useState").value("DRAINING"))
            .andExpect(jsonPath("$.data.actionsReady").value(true));
        assertEquals("MARKET_VERSION_USE_BLOCKED",assertThrows(BusinessException.class,
            ()->service.acquire(identity(),raw(old))).code());
        service.releaseVerified(bundle.bundleId(),proof(bundle));
        mvc.perform(get(path+"/actions/"+stop.operationId()).cookie(cookie)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.state").value("COMPLETED")).andExpect(jsonPath("$.data.afterState").value("DISABLED"));
        var resume=restore(service.snapshot("SH600030",old).revision());
        mvc.perform(post(path+"/actions").cookie(cookie).contentType("application/json").content(mapper.writeValueAsBytes(resume))
            .header("Origin","http://127.0.0.1:15173").header("X-Aistock-Admin-Action","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.afterState").value("ENABLED"));
        assertFalse(service.snapshot("SH600030",old).currentHead());
        assertTrue(service.snapshot("SH600030",current).currentHead());
        assertEquals("ACTIVE",service.acquire(identity(),raw(old)).state());
    }
}
