package com.aistock.scheduler.training;

import java.util.Optional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class TrainingExecutionWorker {
    public interface Algorithm {
        Optional<Receipt> find(TrainingExecutionQueue.Claim claim);
        Receipt submit(TrainingExecutionQueue.Claim claim);
    }

    public record Receipt(TrainingExecutionQueue.Claim claim,String state,String checkpointHash) {}

    private final TrainingExecutionQueue queue;
    private final Algorithm algorithm;

    public TrainingExecutionWorker(TrainingExecutionQueue queue,Algorithm algorithm) {
        this.queue=queue;
        this.algorithm=algorithm;
    }

    public void tick() {
        if(TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Training execution requires a transaction-free caller");
        var active=queue.running();
        if(active==null)active=queue.claim();
        if(active==null)return;
        final var claim=active;
        final Receipt receipt;
        try {receipt=algorithm.find(claim).orElseGet(()->algorithm.submit(claim));}
        catch(RuntimeException error){return;} // Keep the original slot while the outcome is unknown.
        if(receipt==null||!claim.equals(receipt.claim()))return;
        if("SUCCEEDED".equals(receipt.state())&&receipt.checkpointHash()!=null
            &&receipt.checkpointHash().matches("[a-f0-9]{64}"))
            queue.finish(claim,TrainingExecutionQueue.Terminal.SUCCEEDED,receipt.checkpointHash());
        else if("FAILED".equals(receipt.state())&&receipt.checkpointHash()==null)
            queue.finish(claim,TrainingExecutionQueue.Terminal.FAILED,null);
    }
}
