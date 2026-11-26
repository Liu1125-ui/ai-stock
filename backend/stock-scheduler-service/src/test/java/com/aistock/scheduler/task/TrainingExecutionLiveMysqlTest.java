package com.aistock.scheduler.task;

import com.aistock.scheduler.training.TrainingExecutionQueue;
import com.aistock.scheduler.training.TrainingExecutionWorker;
import com.aistock.scheduler.training.TrainingInbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

@Tag("mysql")
@EnabledIfEnvironmentVariable(named="TRAINING_TEST_ALGORITHM_URL",matches="http://127[.]0[.]0[.]1:[0-9]+")
class TrainingExecutionLiveMysqlTest {
    @Test void realAlgorithmFinishesOriginalTaskAfterSubmitReplyIsLost() throws Exception {
        var fixture=new TaskMysqlTest();
        fixture.repo();
        fixture.jdbc.update("UPDATE training_execution_slot SET task_id=NULL WHERE slot_id=1");
        fixture.jdbc.update("DELETE FROM training_dispatch_inbox");
        var clock=Clock.systemUTC();
        var inbox=new TrainingInbox(fixture.jdbc,clock);
        var item=inbox.receive(new TrainingInbox.Envelope(UUID.randomUUID().toString(),"five-day",
            System.getenv("TRAINING_TEST_PRESET_HASH"),System.getenv("TRAINING_TEST_DATA_HASH")));
        var queue=new TrainingExecutionQueue(fixture.jdbc,clock);
        var client=new HttpTrainingExecutionClient(System.getenv("TRAINING_TEST_ALGORITHM_URL"),
            "training-integration-fixture",new ObjectMapper());
        new TrainingExecutionWorker(queue,new TrainingExecutionWorker.Algorithm(){
            @Override public Optional<TrainingExecutionWorker.Receipt> find(TrainingExecutionQueue.Claim claim) {
                return client.find(claim);
            }
            @Override public TrainingExecutionWorker.Receipt submit(TrainingExecutionQueue.Claim claim) {
                client.submit(claim);
                throw new IllegalStateException("Simulated lost response");
            }
        }).tick();
        var claim=queue.running();
        assertNotNull(claim);
        assertEquals(item,claim.item());
        var recovering=new TrainingExecutionWorker(queue,new TrainingExecutionWorker.Algorithm(){
            @Override public Optional<TrainingExecutionWorker.Receipt> find(TrainingExecutionQueue.Claim active) {
                assertEquals(claim,active);
                return client.find(active);
            }
            @Override public TrainingExecutionWorker.Receipt submit(TrainingExecutionQueue.Claim ignored) {
                fail("A receipt already exists for the original task");
                return null;
            }
        });
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        while(queue.running()!=null&&System.nanoTime()<deadline) {
            recovering.tick();
            Thread.sleep(250);
        }
        assertNull(queue.running());
        assertEquals("SUCCEEDED",fixture.jdbc.queryForObject(
            "SELECT execution_state FROM training_dispatch_inbox WHERE task_id=?",String.class,item.taskId()));
        var receipt=client.find(claim).orElseThrow();
        assertEquals(receipt.checkpointHash(),fixture.jdbc.queryForObject(
            "SELECT checkpoint_hash FROM training_dispatch_inbox WHERE task_id=?",String.class,item.taskId()));
        String prediction=System.getenv("TRAINING_TEST_PREDICTION_URL");
        if(prediction!=null) {
            assertTrue(prediction.matches("http://127[.]0[.]0[.]1:[0-9]+"));
            var registrationWorker=new TrainingCandidateRegistrationWorker(fixture.jdbc,clock,new ObjectMapper(),
                System.getenv("TRAINING_TEST_ALGORITHM_URL"),prediction,"training-integration-fixture");
            registrationWorker.tick();
            var modelId=fixture.jdbc.queryForObject(
                "SELECT registered_model_id FROM training_dispatch_inbox WHERE task_id=?",String.class,item.taskId());
            assertNotNull(modelId);
            registrationWorker.tick();
            assertEquals(modelId,fixture.jdbc.queryForObject(
                "SELECT registered_model_id FROM training_dispatch_inbox WHERE task_id=?",String.class,item.taskId()));
            var http=new PredictionTaskHttp("training-integration-fixture",new ObjectMapper());
            var pending=assertThrows(PredictionTaskHttp.Remote.class,()->http.call(prediction,
                "/internal/v1/models/"+modelId+"/publication",null,java.time.Duration.ofSeconds(4)));
            assertEquals(409,pending.status);
            assertEquals("MODEL_NOT_PUBLISHED",pending.code);
        }
        var invalid=inbox.receive(new TrainingInbox.Envelope(UUID.randomUUID().toString(),"five-day",
            "0".repeat(64),item.datasetHash()));
        new TrainingExecutionWorker(queue,client).tick();
        assertNull(queue.running());
        assertEquals("FAILED",fixture.jdbc.queryForObject(
            "SELECT execution_state FROM training_dispatch_inbox WHERE task_id=?",String.class,invalid.taskId()));
    }
}
