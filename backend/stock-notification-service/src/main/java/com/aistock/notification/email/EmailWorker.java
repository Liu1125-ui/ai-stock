package com.aistock.notification.email;

import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class EmailWorker {
    private final EmailDeliveryRepository repository;
    private final MailPayloadCodec codec;
    private final MailTransport transport;
    private final TransactionTemplate transaction;
    public EmailWorker(JdbcTemplate jdbc, MailPayloadCodec codec, MailTransport transport) {
        repository = new EmailDeliveryRepository(jdbc); this.codec = codec; this.transport = transport;
        transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    }
    public int runOnce(Instant now) {
        repository.expire(now);
        var claimed = transaction.execute(status -> repository.claim(now));
        if (claimed == null || claimed.isEmpty()) return 0;
        var claim = claimed.get();
        if (claim.attempt() > 4) {
            repository.finish(claim, "FAILED", now, "RETRY_EXHAUSTED"); return 1;
        }
        boolean sent;
        try { transport.send(codec.decrypt(claim.requestId(), claim.payload())); sent = true; }
        catch (RuntimeException failure) { sent = false; }
        if (sent) repository.finish(claim, "SENT", now, null);
        else {
            long delay = new long[]{5, 30, 120}[Math.min(claim.attempt() - 1, 2)];
            boolean terminal = claim.attempt() >= 4;
            repository.finish(claim, terminal ? "FAILED" : "PENDING", now.plusSeconds(delay), "SMTP_SEND_FAILED");
        }
        return 1;
    }
}
