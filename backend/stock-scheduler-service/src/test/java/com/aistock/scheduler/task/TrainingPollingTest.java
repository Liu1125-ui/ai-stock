package com.aistock.scheduler.task;

import com.aistock.scheduler.training.TrainingPolling;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainingPollingTest {
    @Test void stalledExecutionPollingDoesNotDelayCandidatePolling() throws Exception {
        var executionStarted=new CountDownLatch(1);
        var candidatePolled=new CountDownLatch(1);
        var release=new CountDownLatch(1);
        try(var polling=new TrainingPolling(()->{
            executionStarted.countDown();
            try {release.await();}
            catch(InterruptedException error) {Thread.currentThread().interrupt();}
        },1000,candidatePolled::countDown,1000)) {
            polling.start();
            assertTrue(executionStarted.await(3,TimeUnit.SECONDS));
            assertTrue(candidatePolled.await(3,TimeUnit.SECONDS));
        } finally {release.countDown();}
    }
}
