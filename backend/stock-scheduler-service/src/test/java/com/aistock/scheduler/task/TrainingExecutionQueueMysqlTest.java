package com.aistock.scheduler.task;

import com.aistock.scheduler.training.TrainingExecutionQueue;
import com.aistock.scheduler.training.TrainingInbox;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("mysql")
class TrainingExecutionQueueMysqlTest {
    @Test void competingSchedulersShareOneSlotAndOnlyQueuedTasksCanBeCancelled() throws Exception {
        var fixture=new TaskMysqlTest();
        fixture.repo();
        fixture.jdbc.update("UPDATE training_execution_slot SET task_id=NULL WHERE slot_id=1");
        fixture.jdbc.update("DELETE FROM training_dispatch_inbox");
        var clock=Clock.systemUTC();
        var inbox=new TrainingInbox(fixture.jdbc,clock);
        var a=inbox.receive(item());
        var b=inbox.receive(item());
        var queue=new TrainingExecutionQueue(fixture.jdbc,clock);
        var otherProcess=new TrainingExecutionQueue(fixture.jdbc,clock);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(queue::claim);
            var second=pool.submit(otherProcess::claim);
            var one=first.get(8,TimeUnit.SECONDS);
            var two=second.get(8,TimeUnit.SECONDS);
            assertTrue((one==null) != (two==null));
            var winner=one==null?two:one;
            var waiting=winner.item().equals(a)?b:a;
            assertFalse(queue.cancelQueued(winner.item().taskId()));
            assertTrue(queue.cancelQueued(waiting.taskId()));
            var next=inbox.receive(item());
            assertNull(queue.claim());
            assertFalse(queue.finish(new TrainingExecutionQueue.Claim(winner.item(),UUID.randomUUID().toString()),
                TrainingExecutionQueue.Terminal.SUCCEEDED,"c".repeat(64)));
            assertNull(otherProcess.claim());
            assertTrue(queue.finish(winner,TrainingExecutionQueue.Terminal.SUCCEEDED,"c".repeat(64)));
            var following=otherProcess.claim();
            assertEquals(next,following.item());
            assertFalse(queue.finish(winner,TrainingExecutionQueue.Terminal.FAILED,null));
            assertTrue(otherProcess.finish(following,TrainingExecutionQueue.Terminal.FAILED,null));
            assertNull(queue.claim());
        } finally {
            pool.shutdownNow();
            fixture.jdbc.update("UPDATE training_execution_slot SET task_id=NULL WHERE slot_id=1");
            fixture.jdbc.update("DELETE FROM training_dispatch_inbox");
        }
    }

    private TrainingInbox.Envelope item() {
        return new TrainingInbox.Envelope(UUID.randomUUID().toString(),"five-day","a".repeat(64),"b".repeat(64));
    }
}
