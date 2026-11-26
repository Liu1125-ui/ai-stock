package com.aistock.prediction.modelcatalog;

import com.aistock.common.BusinessException;
import com.aistock.prediction.analysis.*;
import com.aistock.prediction.explanation.MaasClient;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("mysql") class AnalysisAdmissionMysqlTest {
    final ObjectMapper mapper=new ObjectMapper();
    final Instant now=Instant.parse("2026-10-07T07:00:00Z");
    final Clock clock=Clock.fixed(now,ZoneOffset.UTC);
    AnalysisInput input(){return new AnalysisInput(UUID.randomUUID(),"SH600030",LocalDate.parse("2026-07-07"),LocalDate.parse("2026-10-07"),UUID.randomUUID());}
    MaasClient client(){var c=mock(MaasClient.class);when(c.enabled()).thenReturn(true);when(c.model()).thenReturn("test");return c;}

    @Test void operationIsCommittedBeforeEvidenceAndNetworkHasNoTransaction(){
        var db=new ModelDatabaseFixture();var repo=new AnalysisRepository(db.jdbc,mapper);var in=input();var user=UUID.randomUUID();var c=client();
        AnalysisMarketClient market=(id,from,to,version)->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(1,db.jdbc.queryForObject("select count(*) from stock_analysis_operation where operation_id=? and state='RUNNING'",Integer.class,in.operationId().toString()));
            throw new BusinessException("MARKET_UNAVAILABLE","test",HttpStatus.SERVICE_UNAVAILABLE);
        };
        var svc=new AnalysisService(repo,market,c,mapper,clock);
        assertThrows(BusinessException.class,()->svc.generate(user,in));
        assertEquals("FAILED",repo.replay(user,in,now).path("state").asText());
        verify(c,never()).generateAnalysis(any());
    }

    @Test void sameRequestWhileEvidenceIsWaitingDoesNotFetchAgain()throws Exception{
        var db=new ModelDatabaseFixture();var repo=new AnalysisRepository(db.jdbc,mapper);var in=input();var user=UUID.randomUUID();var c=client();
        var entered=new CountDownLatch(1);var finish=new CountDownLatch(1);var reads=new AtomicInteger();
        AnalysisMarketClient market=(id,from,to,version)->{
            if(reads.incrementAndGet()>1)fail("same operation fetched evidence twice");entered.countDown();try{if(!finish.await(5,TimeUnit.SECONDS))throw new IllegalStateException("barrier timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
            throw new BusinessException("MARKET_UNAVAILABLE","test",HttpStatus.SERVICE_UNAVAILABLE);
        };
        var svc=new AnalysisService(repo,market,c,mapper,clock);var pool=Executors.newSingleThreadExecutor();
        try{
            var first=pool.submit(()->assertThrows(BusinessException.class,()->svc.generate(user,in)));
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            assertEquals("RUNNING",assertDoesNotThrow(()->svc.generate(user,in)).path("state").asText());
            assertEquals(1,reads.get());finish.countDown();first.get(5,TimeUnit.SECONDS);
        }finally{finish.countDown();pool.shutdownNow();}
        verify(c,never()).generateAnalysis(any());
    }

    @Test void evidenceFailureIsDurableAndReplayDoesNotRefetch(){
        var db=new ModelDatabaseFixture();var repo=new AnalysisRepository(db.jdbc,mapper);var in=input();var user=UUID.randomUUID();var c=client();var reads=new AtomicInteger();
        AnalysisMarketClient market=(id,from,to,version)->{reads.incrementAndGet();throw new BusinessException("MARKET_UNAVAILABLE","test",HttpStatus.SERVICE_UNAVAILABLE);};
        var svc=new AnalysisService(repo,market,c,mapper,clock);assertThrows(BusinessException.class,()->svc.generate(user,in));
        var replay=assertDoesNotThrow(()->svc.generate(user,in));assertEquals("FAILED",replay.path("state").asText());assertEquals("MARKET_UNAVAILABLE",replay.path("errorCode").asText());assertEquals(1,reads.get());
        verify(c,never()).generateAnalysis(any());
    }

    AnalysisFacts.Facts facts(String marker){return new AnalysisFacts.Facts(Map.of(),mapper.createObjectNode().put("marker",marker),marker.repeat(64));}

    @Test void wrongGenerationCannotBindOrCompleteAndUnpreparedSuccessIsRejected(){
        var db=new ModelDatabaseFixture();var repo=new AnalysisRepository(db.jdbc,mapper);var in=input();var user=UUID.randomUUID();var claim=repo.begin(user,in,now);
        assertEquals(1,claim.generation());assertFalse(claim.receipt().path("contextReady").asBoolean());assertTrue(claim.receipt().path("contextHash").isNull());
        assertThrows(BusinessException.class,()->repo.bindFacts(in.operationId(),2,facts("a"),now));
        assertThrows(BusinessException.class,()->repo.complete(in.operationId(),2,null,"TEST_FAILURE",now));
        assertThrows(BusinessException.class,()->repo.complete(in.operationId(),1,mapper.createArrayNode(),null,now));
        assertEquals("RUNNING",repo.replay(user,in,now).path("state").asText());
        assertEquals(0,db.jdbc.queryForObject("select count(*) from stock_analysis_result",Integer.class));
    }

    @Test void evidenceIsFrozenOnceAndTerminalCannotBeOverwritten(){
        var db=new ModelDatabaseFixture();var repo=new AnalysisRepository(db.jdbc,mapper);var in=input();var user=UUID.randomUUID();repo.begin(user,in,now);
        assertTrue(repo.bindFacts(in.operationId(),1,facts("a"),now).execute());
        assertFalse(repo.bindFacts(in.operationId(),1,facts("b"),now).execute());
        var done=repo.complete(in.operationId(),1,mapper.createArrayNode().add("first"),null,now.plusSeconds(1));
        var id=UUID.fromString(done.path("analysisId").asText());var saved=repo.get(user,id).toString();
        assertEquals(done,repo.complete(in.operationId(),1,null,"LATE_ERROR",now.plusSeconds(2)));
        assertEquals(saved,repo.get(user,id).toString());assertEquals("a".repeat(64),done.path("contextHash").asText());assertEquals(3,done.path("fencingRevision").asLong());
    }

    @Test void admissionExpiryCannotRestartOrClearUnrelatedPaidSlot(){
        var db=new ModelDatabaseFixture();var repo=new AnalysisRepository(db.jdbc,mapper);var in=input();var user=UUID.randomUUID();repo.begin(user,in,now);
        var other=UUID.randomUUID();new com.aistock.prediction.maas.MaasOperationGate(db.jdbc).lockAndAcquire("PREDICTION_EXPLANATION",other,now.plusSeconds(40));
        assertFalse(repo.bindFacts(in.operationId(),1,facts("a"),now.plusSeconds(51)).execute());
        var replay=repo.replay(user,in,now.plusSeconds(51));assertEquals("FAILED",replay.path("state").asText());assertEquals("AI_INTERRUPTED",replay.path("errorCode").asText());
        assertFalse(repo.begin(user,in,now.plusSeconds(51)).execute());assertEquals(other.toString(),db.jdbc.queryForObject("select operation_id from prediction_explanation_slot",String.class));
    }

    @Test void existingContextCanBeReusedWithoutTakingAnotherPaidSlot(){
        var db=new ModelDatabaseFixture();var repo=new AnalysisRepository(db.jdbc,mapper);var in=input();var user=UUID.randomUUID();repo.claim(user,in,facts("a"),now);var saved=repo.complete(in.operationId(),1,mapper.createArrayNode(),null,now.plusSeconds(1));
        var other=UUID.randomUUID();new com.aistock.prediction.maas.MaasOperationGate(db.jdbc).lockAndAcquire("PREDICTION_EXPLANATION",other,now.plusSeconds(2));
        var second=new AnalysisInput(UUID.randomUUID(),in.instrumentId(),in.from(),in.to(),in.marketVersionId());repo.begin(user,second,now.plusSeconds(3));
        var reused=repo.bindFacts(second.operationId(),1,facts("a"),now.plusSeconds(3));assertFalse(reused.execute());assertEquals(saved.path("analysisId"),reused.receipt().path("analysisId"));
        assertEquals(other.toString(),db.jdbc.queryForObject("select operation_id from prediction_explanation_slot",String.class));assertEquals(1,db.jdbc.queryForObject("select count(*) from stock_analysis_result",Integer.class));
    }

    @Test void concurrentAdmissionHasOnlyOneExecutorAndKeepsRequestFingerprint()throws Exception{
        var db=new ModelDatabaseFixture();var repo=new AnalysisRepository(db.jdbc,mapper);var in=input();var user=UUID.randomUUID();var barrier=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
        try{Callable<AnalysisRepository.Claim> work=()->{barrier.await(5,TimeUnit.SECONDS);return new AnalysisRepository(db.jdbc,mapper).begin(user,in,now);};var a=pool.submit(work);var b=pool.submit(work);assertNotEquals(a.get(5,TimeUnit.SECONDS).execute(),b.get(5,TimeUnit.SECONDS).execute());}finally{pool.shutdownNow();}
        assertEquals(1,db.jdbc.queryForObject("select count(*) from stock_analysis_operation",Integer.class));
        assertThrows(BusinessException.class,()->repo.begin(UUID.randomUUID(),in,now));
        assertThrows(BusinessException.class,()->repo.begin(user,new AnalysisInput(in.operationId(),in.instrumentId(),in.from(),in.to(),in.marketVersionId(),UUID.randomUUID()),now));
    }

    @Test void concurrentTerminalWritersCommitOnlyOneResult()throws Exception{
        var db=new ModelDatabaseFixture();var repo=new AnalysisRepository(db.jdbc,mapper);var in=input();var user=UUID.randomUUID();repo.claim(user,in,facts("a"),now);var barrier=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
        try{var success=pool.submit(()->{barrier.await(5,TimeUnit.SECONDS);return new AnalysisRepository(db.jdbc,mapper).complete(in.operationId(),1,mapper.createArrayNode().add("first"),null,now.plusSeconds(1));});var failure=pool.submit(()->{barrier.await(5,TimeUnit.SECONDS);return new AnalysisRepository(db.jdbc,mapper).complete(in.operationId(),1,null,"TEST_FAILURE",now.plusSeconds(1));});var a=success.get(5,TimeUnit.SECONDS);var b=failure.get(5,TimeUnit.SECONDS);assertEquals(a,b);assertEquals(3,a.path("fencingRevision").asLong());assertEquals(a.path("state").asText().equals("SUCCEEDED")?1:0,db.jdbc.queryForObject("select count(*) from stock_analysis_result",Integer.class));}finally{pool.shutdownNow();}
    }

    @Test void predictionReferenceIsRecoverableBeforeAnyFactsExist(){
        var db=new ModelDatabaseFixture();var repo=new AnalysisRepository(db.jdbc,mapper);var base=input();var in=new AnalysisInput(base.operationId(),base.instrumentId(),base.from(),base.to(),base.marketVersionId(),UUID.randomUUID());var user=UUID.randomUUID();repo.begin(user,in,now);
        assertEquals(in.predictionId().toString(),repo.replay(user,in,now).path("predictionId").asText());
    }
}
