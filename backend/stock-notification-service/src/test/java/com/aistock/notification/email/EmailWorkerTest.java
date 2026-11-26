package com.aistock.notification.email;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import static org.junit.jupiter.api.Assertions.*;

@Tag("mysql")
class EmailWorkerTest {
    @Test void reclaimedLeaseRejectsLateOldWorkerCompletion() {
        var db = new EmailDatabaseFixture();
        MailTransport transport = new MailTransport() {
            public boolean available() { return true; }
            public void send(EmailCommand command) { }
        };
        var service = new EmailDeliveryService(db.jdbc, db.codec(), transport, db.clock);
        var repository = new EmailDeliveryRepository(db.jdbc);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(db.jdbc.getDataSource()));
        var command = new EmailCommand(UUID.randomUUID(), UUID.randomUUID(), "member@example.com", "123456", db.clock.instant().plusSeconds(600));
        service.accept(command);
        var first = transaction.execute(status -> repository.claim(db.clock.instant()).orElseThrow());
        // Simulate SMTP accepted followed by worker crash before recording success.
        var restarted = new EmailWorker(db.jdbc, db.codec(), transport);
        restarted.runOnce(db.clock.instant().plusSeconds(31));
        assertEquals("SENT", service.find(command.requestId()).state());
        assertFalse(repository.finish(first, "FAILED", db.clock.instant().plusSeconds(32), "OLD_WORKER"));
        assertEquals("SENT", service.find(command.requestId()).state());
        assertEquals(2, db.jdbc.queryForObject("select attempt_count from email_delivery where request_id=?", Integer.class, command.requestId().toString()));
    }
    @Test void retriesTerminateAfterThreeRetries() {
        var db = new EmailDatabaseFixture();
        MailTransport failing = new MailTransport() {
            public boolean available() { return true; }
            public void send(EmailCommand command) { throw new IllegalStateException("test failure"); }
        };
        var service = new EmailDeliveryService(db.jdbc, db.codec(), failing, db.clock);
        var worker = new EmailWorker(db.jdbc, db.codec(), failing);
        var command = new EmailCommand(UUID.randomUUID(), UUID.randomUUID(), "member@example.com", "123456", db.clock.instant().plusSeconds(600));
        service.accept(command);
        for (long seconds : new long[]{0,5,35,155,156}) worker.runOnce(db.clock.instant().plusSeconds(seconds));
        assertEquals("FAILED", service.find(command.requestId()).state());
        assertEquals(4, db.jdbc.queryForObject("select attempt_count from email_delivery where request_id=?", Integer.class, command.requestId().toString()));
        assertNull(db.jdbc.queryForObject("select payload_ciphertext from email_delivery where request_id=?", byte[].class, command.requestId().toString()));
    }
    @Test void concurrentWorkersDoNotSendSameClaimTwice() throws Exception {
        var db = new EmailDatabaseFixture();
        var sent = new java.util.concurrent.atomic.AtomicInteger();
        MailTransport transport = new MailTransport() {
            public boolean available() { return true; }
            public void send(EmailCommand command) { sent.incrementAndGet(); }
        };
        var service = new EmailDeliveryService(db.jdbc, db.codec(), transport, db.clock);
        var command = new EmailCommand(UUID.randomUUID(), UUID.randomUUID(), "member@example.com", "123456", db.clock.instant().plusSeconds(600));
        service.accept(command);
        var start = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> run = () -> { start.await(); return new EmailWorker(db.jdbc, db.codec(), transport).runOnce(db.clock.instant()); };
            var first = executor.submit(run); var second = executor.submit(run); start.countDown();
            assertEquals(1, first.get(10, java.util.concurrent.TimeUnit.SECONDS) + second.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, sent.get());
        } finally { executor.shutdownNow(); }
    }
    @Test void acceptedMailIsSentOnceAndEncryptedPayloadIsCleared() {
        var db = new EmailDatabaseFixture();
        var sent = new java.util.concurrent.atomic.AtomicInteger();
        MailTransport transport = new MailTransport() {
            public boolean available() { return true; }
            public void send(EmailCommand command) { sent.incrementAndGet(); }
        };
        var service = new EmailDeliveryService(db.jdbc, db.codec(), transport, db.clock);
        var worker = new EmailWorker(db.jdbc, db.codec(), transport);
        var command = new EmailCommand(UUID.randomUUID(), UUID.randomUUID(), "member@example.com", "123456", db.clock.instant().plusSeconds(600));
        service.accept(command);
        worker.runOnce(db.clock.instant());
        worker.runOnce(db.clock.instant());
        assertEquals("SENT", service.find(command.requestId()).state());
        assertEquals(1, sent.get());
        assertNull(db.jdbc.queryForObject("select payload_ciphertext from email_delivery where request_id=?", byte[].class, command.requestId().toString()));
    }
    @Test void failuresRetryWithSameRequestAndStopAtExpiry() {
        var db = new EmailDatabaseFixture();
        MailTransport failing = new MailTransport() {
            public boolean available() { return true; }
            public void send(EmailCommand command) { throw new IllegalStateException("test-only secret provider error"); }
        };
        var service = new EmailDeliveryService(db.jdbc, db.codec(), failing, db.clock);
        var worker = new EmailWorker(db.jdbc, db.codec(), failing);
        var command = new EmailCommand(UUID.randomUUID(), UUID.randomUUID(), "member@example.com", "123456", db.clock.instant().plusSeconds(60));
        service.accept(command);
        worker.runOnce(db.clock.instant());
        assertEquals("PENDING", service.find(command.requestId()).state());
        assertEquals("SMTP_SEND_FAILED", db.jdbc.queryForObject("select last_error_code from email_delivery where request_id=?", String.class, command.requestId().toString()));
        worker.runOnce(db.clock.instant().plusSeconds(4));
        assertEquals(1, db.jdbc.queryForObject("select attempt_count from email_delivery where request_id=?", Integer.class, command.requestId().toString()));
        worker.runOnce(db.clock.instant().plusSeconds(5));
        assertEquals(2, db.jdbc.queryForObject("select attempt_count from email_delivery where request_id=?", Integer.class, command.requestId().toString()));
        worker.runOnce(command.expiresAt());
        assertEquals("EXPIRED", service.find(command.requestId()).state());
    }
}
