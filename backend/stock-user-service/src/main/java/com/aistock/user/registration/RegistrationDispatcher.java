package com.aistock.user.registration;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class RegistrationDispatcher {
    private final RegistrationDispatchRepository repository;
    private final RegistrationPayloadCodec codec;
    private final MailDeliveryClient client;
    private final String token;
    private final TransactionTemplate transaction;
    public RegistrationDispatcher(JdbcTemplate jdbc,RegistrationPayloadCodec codec,MailDeliveryClient client,String token) {
        repository=new RegistrationDispatchRepository(jdbc); this.codec=codec; this.client=client; this.token=token;
        transaction=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    }
    public int runOnce(Instant now) {
        repository.expire(now);
        var claimed=transaction.execute(status->repository.claim(now));
        if(claimed==null||claimed.isEmpty()) return 0;
        var claim=claimed.get();
        try {
            if(claim.attempt()>4) { repository.finish(claim,"FAILED",now,"RETRY_EXHAUSTED",now); return 1; }
            var response=client.accept(codec.decrypt(claim.requestId(),claim.payload()),token);
            if(response==null||!"OK".equals(response.code())||response.data()==null||!claim.requestId().equals(response.data().requestId())) throw new IllegalStateException("Invalid mail acknowledgement");
            repository.finish(claim,"ACCEPTED",now,null,now);
        } catch(RuntimeException unavailable) {
            long delay=new long[]{5,30,120}[Math.min(claim.attempt()-1,2)];
            repository.finish(claim,claim.attempt()>=4?"FAILED":"PENDING",now.plusSeconds(delay),"EMAIL_SERVICE_UNAVAILABLE",now);
        }
        return 1;
    }
}
