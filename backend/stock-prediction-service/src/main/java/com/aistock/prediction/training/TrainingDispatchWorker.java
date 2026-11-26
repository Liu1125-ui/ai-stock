package com.aistock.prediction.training;

import com.aistock.prediction.training.TrainingAdmissionRepository.Item;
import java.util.Optional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class TrainingDispatchWorker {
    public interface Scheduler {
        Optional<Item> find(Item item);
        Item receive(Item item);
    }
    public static final class Conflict extends RuntimeException {}

    private final TrainingDispatchStore store;
    private final Scheduler scheduler;

    public TrainingDispatchWorker(TrainingDispatchStore store,Scheduler scheduler) {
        this.store=store;
        this.scheduler=scheduler;
    }

    @Scheduled(fixedDelayString="${aistock.training.dispatch-delay-ms:15000}",initialDelayString="${aistock.training.dispatch-delay-ms:15000}")
    public void runBatch() {
        if(TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Training dispatch requires a transaction-free caller");
        for(int i=0;i<5;i++) {
            var claim=store.claim();
            if(claim==null)return;
            var outcome=TrainingDispatchStore.Outcome.RETRY;
            try {
                var original=claim.item();
                var result=claim.reconcileFirst()?scheduler.find(original):Optional.<Item>empty();
                var receipt=result.orElseGet(()->scheduler.receive(original));
                if(original.equals(receipt))outcome=TrainingDispatchStore.Outcome.CONFIRMED;
            } catch(Conflict error) {
                outcome=TrainingDispatchStore.Outcome.CONFLICT;
            } catch(RuntimeException error) {
                // An unknown result preserves the original task number for lookup.
            }
            store.finish(claim,outcome);
            if(Thread.currentThread().isInterrupted())return;
        }
    }
}
