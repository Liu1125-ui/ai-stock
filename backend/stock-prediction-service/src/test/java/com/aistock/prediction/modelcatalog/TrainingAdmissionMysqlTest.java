package com.aistock.prediction.modelcatalog;

import com.aistock.common.BusinessException;
import com.aistock.common.training.TrainingSubmission;
import com.aistock.prediction.training.TrainingAdmissionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("mysql")
class TrainingAdmissionMysqlTest {
    @Test void replaysOriginalItemsAndDoesNotRebindChangedCatalog() {
        var fixture=new ModelDatabaseFixture();
        var repository=new TrainingAdmissionRepository(fixture.jdbc,new ObjectMapper(),Clock.systemUTC());
        var owner=UUID.randomUUID();
        String request=UUID.randomUUID().toString();
        var admitted=repository.admit(owner,new TrainingSubmission(request,List.of("b","a")),id->configuration());
        var replay=repository.admit(owner,new TrainingSubmission(request,List.of("a","b")),id->{fail("must replay original");return null;});
        assertEquals(admitted,replay);
        assertEquals("TRAINING_REQUEST_CONFLICT",assertThrows(BusinessException.class,
            ()->repository.admit(owner,new TrainingSubmission(request,List.of("a")),id->configuration())).code());
        var other=repository.admit(UUID.randomUUID(),new TrainingSubmission(request,List.of("a")),id->configuration());
        assertNotEquals(admitted.get(0).taskId(),other.get(0).taskId());
    }

    @Test void rollsBackWholeBatchWhenAnyConfigurationCannotBeFrozen() {
        var fixture=new ModelDatabaseFixture();
        var repository=new TrainingAdmissionRepository(fixture.jdbc,new ObjectMapper(),Clock.systemUTC());
        var request=new TrainingSubmission(UUID.randomUUID().toString(),List.of("a","missing"));
        assertEquals("TRAINING_CONFIGURATION_UNAVAILABLE",assertThrows(BusinessException.class,
            ()->repository.admit(UUID.randomUUID(),request,id->id.equals("a")?configuration():null)).code());
        assertEquals(0,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM training_submission",Integer.class));
        assertEquals(0,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM training_submission_item",Integer.class));
    }

    @Test void concurrentSubmissionHasOneDurableTaskIdentity() throws Exception {
        var fixture=new ModelDatabaseFixture();
        var repository=new TrainingAdmissionRepository(fixture.jdbc,new ObjectMapper(),Clock.systemUTC());
        var owner=UUID.randomUUID();
        var request=new TrainingSubmission(UUID.randomUUID().toString(),List.of("a"));
        var gate=new java.util.concurrent.CountDownLatch(1);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.Callable<List<TrainingAdmissionRepository.Item>> submit=()->{
            assertTrue(gate.await(5,java.util.concurrent.TimeUnit.SECONDS));
            return repository.admit(owner,request,id->configuration());
        };
        try {
            var a=pool.submit(submit);
            var b=pool.submit(submit);
            gate.countDown();
            assertEquals(a.get(8,java.util.concurrent.TimeUnit.SECONDS),b.get(8,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM training_submission_item",Integer.class));
        } finally {pool.shutdownNow();}
    }

    @Test void replayReadsCommittedChildrenEvenWithAnOlderTransactionSnapshot() {
        var fixture=new ModelDatabaseFixture();
        var repository=new TrainingAdmissionRepository(fixture.jdbc,new ObjectMapper(),Clock.systemUTC());
        var owner=UUID.randomUUID();
        var request=new TrainingSubmission(UUID.randomUUID().toString(),List.of("a"));
        var outer=new org.springframework.transaction.support.TransactionTemplate(
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(fixture.jdbc.getDataSource()));
        outer.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        var pool=java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            outer.executeWithoutResult(transaction->{
                assertEquals(0,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM training_submission_item",Integer.class));
                var first=pool.submit(()->repository.admit(owner,request,id->configuration()));
                try {
                    var original=first.get(8,java.util.concurrent.TimeUnit.SECONDS);
                    assertEquals(original,repository.admit(owner,request,id->{fail("must use current committed row");return null;}));
                } catch(InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(error);
                } catch(java.util.concurrent.ExecutionException|java.util.concurrent.TimeoutException error) {
                    throw new AssertionError(error);
                }
            });
        } finally {pool.shutdownNow();}
    }

    private TrainingAdmissionRepository.Configuration configuration() {
        return new TrainingAdmissionRepository.Configuration("a".repeat(64),"b".repeat(64));
    }
}
