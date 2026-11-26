package com.aistock.scheduler.task;
import com.aistock.common.BusinessException;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("mysql") class TaskMysqlTest {
    JdbcTemplate jdbc;
    private final Instant now=Instant.parse("2026-10-02T12:01:00Z");
    private final LocalDate date=LocalDate.of(2026,10,2);
    TaskRepository repo() {
        var url=System.getenv("SCHEDULER_TEST_DB_URL");var secret=System.getenv("SCHEDULER_TEST_DB_PASSWORD");
        if(!"jdbc:mysql://127.0.0.1:13316/stock_scheduler_test?serverTimezone=UTC".equals(url)||secret==null||secret.isBlank())throw new IllegalStateException("isolated scheduler test database required");
        var ds=new DriverManagerDataSource(url,"root",secret);ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc=new JdbcTemplate(ds);assertEquals("stock_scheduler_test",jdbc.queryForObject("select database()",String.class));
        jdbc.update("delete from comparison_update_context");jdbc.update("delete from task_attempt");jdbc.update("delete from admin_market_refresh_operation" );jdbc.update("delete from admin_market_refresh_guard" );jdbc.update("delete from admin_market_refresh_context" );jdbc.update("delete from scheduled_task");jdbc.update("delete from market_update_plan");jdbc.update("delete from verification_scan");jdbc.update("delete from verification_revision_scan");return new TaskRepository(jdbc);
    }
    @Test void keyReplayAndConflict() {
        var r=repo();var first=r.create("SH600030",date,"key-1",now);
        assertEquals(first.taskId(),r.create("SH600030",date,"key-1",now).taskId());
        assertThrows(BusinessException.class,()->r.create("SZ000002",date,"key-1",now));
        assertEquals(1,r.list().size());
    }
    @Test void concurrentWorkersOnlyClaimOnce()throws Exception {
        var r=repo();r.create("SH600030",date,"claim",now);var pool=Executors.newFixedThreadPool(2);
        try{var a=pool.submit(()->r.claim(now));var b=pool.submit(()->r.claim(now));assertEquals(1,java.util.stream.Stream.of(a.get(),b.get()).filter(Objects::nonNull).count());}
        finally{pool.shutdownNow();}
    }
    @Test void expiredLeaseRecoversAndOldTokenCannotFinish() {
        var r=repo();var task=r.create("SH600030",date,"recover",now);var old=r.claim(now);
        assertNull(r.claim(now.plusSeconds(91)));
        assertEquals("RECONCILING",r.get(task.taskId()).state());
        assertFalse(r.finish(old,new MarketRefreshClient.Result(old.attemptId(),"AVAILABLE",UUID.randomUUID().toString(),null),now.plusSeconds(92)));
        assertNull(r.claim(now.plusSeconds(152)));
        assertEquals(1,r.attempts(task.taskId()).size());
    }
    @Test void sourceTimeoutBackoffAndInvalidDataStops() {
        var r=repo();var task=r.create("SH600030",date,"retry",now);var claim=r.claim(now);
        r.finish(claim,new MarketRefreshClient.Result(claim.attemptId(),"FAILED",null,"SOURCE_TIMEOUT"),now.plusSeconds(1));
        assertEquals("RETRY_WAIT",r.get(task.taskId()).state());assertNull(r.claim(now.plusSeconds(60)));
        var next=r.claim(now.plusSeconds(61));
        r.finish(next,new MarketRefreshClient.Result(next.attemptId(),"FAILED",null,"MARKET_DATA_INCOMPLETE"),now.plusSeconds(62));
        assertEquals("FAILED",r.get(task.taskId()).state());r.retry(task.taskId(),now.plusSeconds(63));
        assertEquals("QUEUED",r.get(task.taskId()).state());assertEquals(2,r.attempts(task.taskId()).size());
    }
    @Test void retryLimitAndQueuedCancellation() {
        var r=repo();var task=r.create("SH600030",date,"limit",now);var clock=now;
        for(int i=0;i<3;i++){var c=r.claim(clock);assertNotNull(c);r.finish(c,new MarketRefreshClient.Result(c.attemptId(),"FAILED",null,"SOURCE_TIMEOUT"),clock.plusSeconds(1));clock=clock.plusSeconds(i==0?61:121);}
        assertEquals("FAILED",r.get(task.taskId()).state());r.retry(task.taskId(),clock);r.cancel(task.taskId(),clock);
        assertEquals("CANCELLED",r.get(task.taskId()).state());assertNull(r.claim(clock));
        var running=r.create("SZ000002",date,"running",now);r.claim(now);
        assertThrows(BusinessException.class,()->r.cancel(running.taskId(),now));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void dailySourceOutageGetsOneDelayedAttempt(boolean sourceRecovered) {
        var repository = repo();
        var key = "daily-SH600030-" + date;
        var task = repository.create("SH600030", date, key, now);
        var attemptTime = now;
        for (int attempt = 1; attempt <= 3; attempt++) {
            var claim = repository.claim(attemptTime);
            assertNotNull(claim);
            assertTrue(repository.finish(claim,
                new MarketRefreshClient.Result(claim.attemptId(), "FAILED", null, "SOURCE_UNAVAILABLE"), attemptTime));
            if (attempt < 3) attemptTime = attemptTime.plusSeconds(attempt == 1 ? 60 : 120);
        }

        var recoveryTime = attemptTime.plusSeconds(1800);
        repository = new TaskRepository(jdbc);
        var waiting = repository.get(task.taskId());
        assertEquals("RETRY_WAIT", waiting.state());
        assertEquals(recoveryTime, waiting.nextAt());
        assertEquals(waiting, repository.create("SH600030", date, key, attemptTime.plusSeconds(30)));
        assertNull(repository.claim(recoveryTime.minusSeconds(1)));
        var recovery = repository.claim(recoveryTime);
        assertNotNull(recovery);
        assertEquals(task.taskId(), recovery.taskId());
        assertNull(repository.claim(recoveryTime));
        var publishedVersion = sourceRecovered ? UUID.randomUUID().toString() : null;
        var receipt = new MarketRefreshClient.Result(recovery.attemptId(),
            sourceRecovered ? "AVAILABLE" : "FAILED", publishedVersion,
            sourceRecovered ? null : "SOURCE_UNAVAILABLE");
        assertTrue(repository.finish(recovery, receipt, recoveryTime));

        var terminal = repository.get(task.taskId());
        var terminalState = sourceRecovered ? "SUCCEEDED" : "FAILED";
        assertEquals(terminalState, terminal.state());
        assertEquals(publishedVersion, terminal.versionId());
        assertEquals(List.of("FAILED", "FAILED", "FAILED", terminalState),
            repository.attempts(task.taskId()).stream().map(row -> row.get("state")).toList());
        assertEquals(terminal, repository.create("SH600030", date, key, recoveryTime.plusSeconds(30)));
        assertNull(repository.claim(recoveryTime.plusSeconds(3600)));
    }

    @Test void dailyPlanDoesNotDuplicateAndCatchesLatestOnly() {
        var r=repo();var ids=List.of("SH600030","SZ000002");
        r.enqueueDaily(ids,now.minusSeconds(3600));assertEquals(2,r.list().size());
        r.enqueueDaily(ids,now);r.enqueueDaily(ids,now);assertEquals(4,r.list().size());
        r.enqueueDaily(ids,now.plus(Duration.ofDays(4)));assertEquals(6,r.list().size());
    }
    @Test void morningRestartCatchesYesterdayNotFutureToday() {
        var r=repo();var morning=Instant.parse("2026-10-02T01:00:00Z");
        r.enqueueDaily(List.of("SH600030"),morning);
        assertEquals(1,r.list().size());assertEquals(LocalDate.of(2026,10,1),r.list().get(0).targetDate());
        r.enqueueDaily(List.of("SH600030"),morning.plusSeconds(60));assertEquals(1,r.list().size());
    }
    @Test void publishedResultRecoveredAfterLostResponseWithoutNewAttempt() {
        var r=repo();var task=r.create("SH600030",LocalDate.now(ZoneId.of("Asia/Shanghai")),"lost-response",Instant.now());
        var market=org.mockito.Mockito.mock(MarketRefreshClient.class);var receipt=org.mockito.Mockito.mock(MarketReceiptClient.class);
        var version=UUID.randomUUID().toString();
        org.mockito.Mockito.when(market.refresh(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString())).thenThrow(new IllegalStateException("response lost"));
        org.mockito.Mockito.when(receipt.get(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenAnswer(call->com.aistock.common.ApiResponse.success(new MarketRefreshClient.Result(call.getArgument(0),"AVAILABLE",version,null),"t"));
        new TaskWorker(r,market,receipt,org.mockito.Mockito.mock(PredictionTaskClient.class),"t",false).runOnce();
        assertEquals("SUCCEEDED",r.get(task.taskId()).state());assertEquals(version,r.get(task.taskId()).versionId());assertEquals(1,r.attempts(task.taskId()).size());
    }
    @Test void restartReconcilesOldSuccessfulReceiptAndUnknownNeverRetriesBlindly() {
        var r=repo();var present=Instant.now();var task=r.create("SH600030",LocalDate.now(ZoneId.of("Asia/Shanghai")),"restart-receipt",present.minusSeconds(120));
        var old=r.claim(present.minusSeconds(120));var market=org.mockito.Mockito.mock(MarketRefreshClient.class);var receipt=org.mockito.Mockito.mock(MarketReceiptClient.class);
        org.mockito.Mockito.when(receipt.get(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenThrow(new IllegalStateException("temporarily down"));
        var worker=new TaskWorker(r,market,receipt,org.mockito.Mockito.mock(PredictionTaskClient.class),"t",false);worker.runOnce();
        assertEquals("RECONCILING",r.get(task.taskId()).state());assertEquals(1,r.attempts(task.taskId()).size());org.mockito.Mockito.verifyNoInteractions(market);
        var version=UUID.randomUUID().toString();
        org.mockito.Mockito.doReturn(com.aistock.common.ApiResponse.success(new MarketRefreshClient.Result(old.attemptId(),"AVAILABLE",version,null),"t")).when(receipt).get(old.attemptId(),"t");
        worker.runOnce();assertEquals("SUCCEEDED",r.get(task.taskId()).state());assertEquals(version,r.get(task.taskId()).versionId());assertEquals(1,r.attempts(task.taskId()).size());assertEquals("SUCCEEDED",r.attempts(task.taskId()).get(0).get("state"));org.mockito.Mockito.verifyNoInteractions(market);
    }
 @Test void lifecycleDenialStopsFrozenComparisonAndVerificationWithoutAutomaticRetry(){for(String type:List.of("PREDICTION_VERIFY","COMPARISON_UPDATE")){var r=repo();var time=ComparisonTaskMysqlTest.NOW;var day=ComparisonTaskMysqlTest.DAY;var original=ComparisonTaskMysqlTest.snapshot(time);var task=type.equals("COMPARISON_UPDATE")?r.createComparison(original,day,3,time):r.createVerification(UUID.randomUUID().toString(),"SH600030",day,"origin-share-close-v1","lifecycle-"+UUID.randomUUID(),time);var c=r.claim(time,Set.of(type));assertNotNull(c);assertTrue(r.finish(c,new MarketRefreshClient.Result(c.attemptId(),"FAILED",null,"MARKET_VERSION_USE_BLOCKED"),time.plusSeconds(1)));assertEquals("FAILED",r.get(task.taskId()).state());assertEquals(1,r.get(task.taskId()).attemptCount());assertNull(r.claim(time.plusSeconds(3600),Set.of(type)));assertEquals(1,r.attempts(task.taskId()).size());if(type.equals("COMPARISON_UPDATE"))assertEquals(original,r.comparisonContext(task.taskId()).snapshot());}}

}
