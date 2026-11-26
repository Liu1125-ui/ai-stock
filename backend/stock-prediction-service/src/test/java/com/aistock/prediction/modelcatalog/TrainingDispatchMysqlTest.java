package com.aistock.prediction.modelcatalog;

import com.aistock.common.training.TrainingSubmission;
import com.aistock.prediction.training.TrainingAdmissionRepository;
import com.aistock.prediction.training.TrainingAdmissionRepository.Item;
import com.aistock.prediction.training.TrainingDispatchStore;
import com.aistock.prediction.training.TrainingDispatchWorker;
import com.aistock.prediction.training.TrainingSchedulerClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("mysql")
class TrainingDispatchMysqlTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private final ModelDatabaseFixture fixture=new ModelDatabaseFixture();
    private final AtomicReference<Instant> now=new AtomicReference<>(Instant.parse("2026-11-10T00:00:00Z"));
    private final Clock clock=new Clock() {
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return Clock.tick(Clock.fixed(now.get(),zone),Duration.ofMillis(1));}
        @Override public Instant instant(){return now.get();}
    };

    private Item admit() {
        return new TrainingAdmissionRepository(fixture.jdbc,JSON,clock)
            .admit(UUID.randomUUID(),new TrainingSubmission(UUID.randomUUID().toString(),List.of("five-day")),
                id->new TrainingAdmissionRepository.Configuration("a".repeat(64),"b".repeat(64))).get(0);
    }

    private String state(Item item) {
        return fixture.jdbc.queryForObject("SELECT dispatch_state FROM training_submission_item WHERE task_id=?",String.class,item.taskId());
    }

    @Test void committedSchedulerReceiptWithLostReplyIsRecoveredByGetWithoutSecondPost() throws Exception {
        var item=admit();
        var received=new AtomicReference<Item>();
        var writes=new AtomicInteger();
        var reads=new AtomicInteger();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/v1/tasks/training-inbox",exchange->{
            try {
                if(!"training-fixture".equals(exchange.getRequestHeaders().getFirst("X-Service-Token"))) {
                    exchange.sendResponseHeaders(401,-1);
                    return;
                }
                if("POST".equals(exchange.getRequestMethod())) {
                    received.set(JSON.readValue(exchange.getRequestBody(),Item.class));
                    writes.incrementAndGet();
                    exchange.sendResponseHeaders(503,-1);
                } else {
                    reads.incrementAndGet();
                    var bytes=JSON.writeValueAsBytes(com.aistock.common.ApiResponse.success(received.get(),"test"));
                    exchange.sendResponseHeaders(200,bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            } finally {exchange.close();}
        });
        server.start();
        try {
            var worker=new TrainingDispatchWorker(new TrainingDispatchStore(fixture.jdbc,clock),
                new TrainingSchedulerClient("http://127.0.0.1:"+server.getAddress().getPort(),"training-fixture",Duration.ofSeconds(2)));
            worker.runBatch();
            assertEquals(item,received.get());
            assertEquals("PENDING",state(item));
            now.updateAndGet(t->t.plusSeconds(61));
            worker.runBatch();
            assertEquals("CONFIRMED",state(item));
            assertEquals(1,writes.get());
            assertEquals(1,reads.get());
        } finally {server.stop(0);}
    }

    @Test void expiredClaimCannotConfirmAndRecoveryResendsOnlyOriginalIdentity() {
        var item=admit();
        var store=new TrainingDispatchStore(fixture.jdbc,clock);
        var abandoned=store.claim();
        assertNotNull(abandoned);
        assertNull(store.claim());
        now.updateAndGet(t->t.plusSeconds(31));
        assertFalse(store.finish(abandoned,TrainingDispatchStore.Outcome.CONFIRMED));
        var posts=new AtomicInteger();
        var reads=new AtomicInteger();
        var worker=new TrainingDispatchWorker(store,new TrainingDispatchWorker.Scheduler(){
            @Override public Optional<Item> find(Item lookup){assertEquals(item,lookup);reads.incrementAndGet();return Optional.empty();}
            @Override public Item receive(Item post){assertEquals(item,post);posts.incrementAndGet();return post;}
        });
        worker.runBatch();
        assertEquals("CONFIRMED",state(item));
        assertEquals(1,reads.get());
        assertEquals(1,posts.get());
    }

    @Test void unknownLookupDoesNotPostAndDefinitiveConflictStopsRetries() {
        var item=admit();
        var store=new TrainingDispatchStore(fixture.jdbc,clock);
        assertNotNull(store.claim());
        now.updateAndGet(t->t.plusSeconds(31));
        var posts=new AtomicInteger();
        var worker=new TrainingDispatchWorker(store,new TrainingDispatchWorker.Scheduler(){
            @Override public Optional<Item> find(Item lookup){throw new IllegalStateException("unknown");}
            @Override public Item receive(Item post){posts.incrementAndGet();return post;}
        });
        worker.runBatch();
        assertEquals(0,posts.get());
        assertEquals("PENDING",state(item));
        now.updateAndGet(t->t.plusSeconds(61));
        new TrainingDispatchWorker(store,new TrainingDispatchWorker.Scheduler(){
            @Override public Optional<Item> find(Item lookup){return Optional.empty();}
            @Override public Item receive(Item post){throw new TrainingDispatchWorker.Conflict();}
        }).runBatch();
        assertEquals("CONFLICT",state(item));
        assertNull(store.claim());
    }

    @Test void expiryWhileWaitingForRowLockPreventsLateConfirmation() throws Exception {
        var item=admit();
        var store=new TrainingDispatchStore(fixture.jdbc,clock);
        var claim=store.claim();
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        try(var connection=fixture.jdbc.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try(var query=connection.prepareStatement("SELECT task_id FROM training_submission_item WHERE task_id=? FOR UPDATE")) {
                query.setString(1,item.taskId());
                query.executeQuery().close();
            }
            var finish=executor.submit(()->store.finish(claim,TrainingDispatchStore.Outcome.CONFIRMED));
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
            boolean waiting=false;
            while(System.nanoTime()<deadline) {
                if(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits",Integer.class)>0) {
                    waiting=true;
                    break;
                }
                Thread.sleep(10);
            }
            assertTrue(waiting);
            now.updateAndGet(t->t.plusSeconds(31));
            connection.commit();
            assertFalse(finish.get(4,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("PENDING",state(item));
        } finally {executor.shutdownNow();}
    }
}
