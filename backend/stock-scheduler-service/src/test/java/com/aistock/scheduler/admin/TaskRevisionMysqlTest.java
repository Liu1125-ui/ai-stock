package com.aistock.scheduler.admin;

import com.aistock.scheduler.task.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("mysql")
class TaskRevisionMysqlTest {
    private static final Instant NOW=Instant.parse("2026-10-20T12:00:00Z");
    private static final LocalDate DAY=LocalDate.of(2026,10,20);
    private static final Set<String> COMPARISON=Set.of("COMPARISON_UPDATE");
    private JdbcTemplate jdbc;
    private TaskRepository repo;

    @BeforeEach void setUp() {
        String url=System.getenv("SCHEDULER_TEST_DB_URL"), secret=System.getenv("SCHEDULER_TEST_DB_PASSWORD");
        if(!"jdbc:mysql://127.0.0.1:13316/stock_scheduler_test?serverTimezone=UTC".equals(url)||secret==null||secret.isBlank())
            throw new IllegalStateException("Isolated scheduler test database required");
        var ds=new DriverManagerDataSource(url,"root",secret);
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc=new JdbcTemplate(ds);
        assertEquals("stock_scheduler_test",jdbc.queryForObject("select database()",String.class));
        jdbc.update("delete from admin_task_operation");
        jdbc.update("delete from comparison_update_context");
        jdbc.update("delete from task_attempt");
        jdbc.update("delete from admin_market_refresh_operation" );jdbc.update("delete from admin_market_refresh_guard" );jdbc.update("delete from admin_market_refresh_context" );jdbc.update("delete from scheduled_task");
        repo=new TaskRepository(jdbc);
    }

    private TaskRepository.Task create() {return repo.create("SH600030",DAY,"revision-"+UUID.randomUUID(),NOW);}
    private long revision(String id) {return jdbc.queryForObject("select revision from scheduled_task where task_id=?",Long.class,id);}
    private MarketRefreshClient.Result failure(TaskRepository.Claim c,String code) {return new MarketRefreshClient.Result(c.attemptId(),"FAILED",null,code);}
    private ComparisonTaskRecords.Snapshot snapshot() {
        return new ComparisonTaskRecords.Snapshot("SH600030",UUID.randomUUID(),"a".repeat(64),DAY.minusYears(1),DAY,"baostock-comparison-backward-close-v1",NOW);
    }

    @Test void cancelChangesRevisionEvenAtSameMillisecond() {
        var task=create();assertEquals(0,revision(task.taskId()));
        repo.cancel(task.taskId(),NOW);
        assertEquals(1,revision(task.taskId()));
        assertEquals("CANCELLED",repo.get(task.taskId()).state());
    }

    @Test void claimChangesRevisionEvenAtSameMillisecond() {
        var task=create();var c=repo.claim(NOW);
        assertEquals(task.taskId(),c.taskId());assertEquals(1,revision(task.taskId()));
        assertEquals("RUNNING",repo.get(task.taskId()).state());
    }

    @Test void replayAndReadOnlyViewsDoNotChangeRevision() {
        var task=repo.create("SH600030",DAY,"same-key",NOW);
        assertEquals(task.taskId(),repo.create("SH600030",DAY,"same-key",NOW).taskId());
        var service=new AdminTaskService(new AdminTaskRepository(jdbc),new AdminTaskCursorCodec(),Clock.fixed(NOW,ZoneOffset.UTC));
        service.detail(task.taskId());
        service.list(new AdminTaskRecords.TaskQuery(null,null,null,DAY,DAY,20),null);
        service.attempts(task.taskId(),new AdminTaskRecords.AttemptQuery(20),null);
        assertEquals(0,revision(task.taskId()));
    }

    @Test void finishFailureRetryAndCancelEachAdvanceVersionWithoutDeletingAttempts() {
        var task=create();var c=repo.claim(NOW);
        assertTrue(repo.finish(c,failure(c,"SOURCE_INVALID"),NOW));
        assertEquals(2,revision(task.taskId()));
        repo.retry(task.taskId(),NOW);assertEquals(3,revision(task.taskId()));
        repo.cancel(task.taskId(),NOW);assertEquals(4,revision(task.taskId()));
        assertEquals(1,repo.attempts(task.taskId()).size());
    }

    @Test void automaticFailureBackoffAndFinalExhaustionAdvanceVersion() {
        var task=create();var time=NOW;
        for(int i=0;i<3;i++) {
            var c=repo.claim(time);assertNotNull(c);
            assertTrue(repo.finish(c,failure(c,"SOURCE_TIMEOUT"),time));
            assertEquals((i+1)*2,revision(task.taskId()));
            time=time.plusSeconds(i==0?60:120);
        }
        assertEquals("FAILED",repo.get(task.taskId()).state());
    }

    @Test void leaseExpirationAndConfirmedReceiptAdvanceButStaleResponseDoesNot() {
        var task=create();var c=repo.claim(NOW);
        assertNull(repo.claim(NOW.plusSeconds(91)));
        assertEquals("RECONCILING",repo.get(task.taskId()).state());assertEquals(2,revision(task.taskId()));
        var result=new MarketRefreshClient.Result(c.attemptId(),"AVAILABLE",UUID.randomUUID().toString(),null);
        assertFalse(repo.finish(c,result,NOW.plusSeconds(92)));assertEquals(2,revision(task.taskId()));
        assertTrue(repo.reconcile(c,result,NOW.plusSeconds(92)));assertEquals(3,revision(task.taskId()));
    }

    @Test void successfulAndEmptyResultsAdvanceVersion() {
        for(String state:List.of("AVAILABLE","LEGITIMATE_EMPTY")) {
            var task=create();var c=repo.claim(NOW);
            assertTrue(repo.finish(c,new MarketRefreshClient.Result(c.attemptId(),state,UUID.randomUUID().toString(),null),NOW));
            assertEquals(2,revision(task.taskId()));
        }
    }

    @Test void predictionSkippedResultAdvancesVersion() {
        var task=repo.createVerification(UUID.randomUUID().toString(),"SH600030",DAY,"origin-share-close-v1","revision-verification",NOW);
        var c=repo.claim(NOW);
        assertTrue(repo.finish(c,new MarketRefreshClient.Result(c.attemptId(),"SKIPPED_NOT_DUE",null,null),NOW));
        assertEquals("SKIPPED",repo.get(task.taskId()).state());assertEquals(2,revision(task.taskId()));
    }

    @Test void comparisonUnknownAndReceiptBackoffAdvanceVersion() {
        var task=repo.createComparison(snapshot(),DAY,3,NOW);var c=repo.claim(NOW,COMPARISON);
        repo.markComparisonUnknown(c,NOW);assertEquals(2,revision(task.taskId()));
        repo.markComparisonUnknown(c,NOW);assertEquals(2,revision(task.taskId()));
        assertTrue(repo.reconcile(c,failure(c,"SOURCE_TIMEOUT"),NOW));
        assertEquals(3,revision(task.taskId()));assertEquals("RETRY_WAIT",repo.get(task.taskId()).state());
    }

    @Test void comparisonExpiredAndExhaustedQueuedTasksAdvanceVersion() {
        var expired=repo.createComparison(snapshot(),DAY,3,NOW);
        assertNull(repo.claim(NOW.plusSeconds(86400),COMPARISON));assertEquals(1,revision(expired.taskId()));
        var budget=repo.createComparison(new ComparisonTaskRecords.Snapshot("SH600276",UUID.randomUUID(),"b".repeat(64),DAY.minusYears(1),DAY,"baostock-comparison-backward-close-v1",NOW),DAY,1,NOW);
        var c=repo.claim(NOW,COMPARISON);
        assertTrue(repo.finish(c,failure(c,"SOURCE_TIMEOUT"),NOW));
        // A bounded legacy queued fixture tests the existing worker budget guard, not a public retry.
        jdbc.update("update scheduled_task set state='QUEUED',next_at=? where task_id=?",java.sql.Timestamp.from(NOW),budget.taskId());
        assertNull(repo.claim(NOW,COMPARISON));assertEquals(3,revision(budget.taskId()));
        assertEquals("COMPARISON_BUDGET_EXHAUSTED",repo.get(budget.taskId()).errorCode());
    }

    @Test void comparisonManualRetryAdvancesWithoutResettingAttemptCount() {
        var task=repo.createComparison(snapshot(),DAY,3,NOW);var c=repo.claim(NOW,COMPARISON);
        assertTrue(repo.finish(c,failure(c,"SOURCE_TIMEOUT"),NOW));
        // Interrupted legacy retry-wait is represented as a recoverable failed fixture.
        jdbc.update("update scheduled_task set state='FAILED' where task_id=?",task.taskId());
        repo.retry(task.taskId(),NOW);assertEquals(3,revision(task.taskId()));
        assertEquals(1,repo.get(task.taskId()).attemptCount());
    }

    @Test void storageUsesBigintAndRejectsNegativeRevision() {
        var task=create();
        jdbc.update("update scheduled_task set revision=9007199254740993 where task_id=?",task.taskId());
        repo.cancel(task.taskId(),NOW);assertEquals(9007199254740994L,revision(task.taskId()));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("update scheduled_task set revision=-1 where task_id=?",task.taskId()));
        assertEquals(0,jdbc.queryForObject("select count(*) from admin_task_operation",Integer.class));
    }
}
