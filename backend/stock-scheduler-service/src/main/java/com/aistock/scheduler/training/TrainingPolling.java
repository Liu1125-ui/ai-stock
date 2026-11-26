package com.aistock.scheduler.training;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/** Training HTTP polls use separate threads from market and notification schedules. */
public final class TrainingPolling implements AutoCloseable {
    private static final Logger log=LoggerFactory.getLogger(TrainingPolling.class);
    private final ScheduledExecutorService executor;
    private final AtomicBoolean started=new AtomicBoolean();
    private final Runnable execution;
    private final Runnable registration;
    private final long executionDelay;
    private final long registrationDelay;

    public TrainingPolling(Runnable execution,long executionDelay,Runnable registration,long registrationDelay) {
        if(executionDelay<1000||registrationDelay<1000)
            throw new IllegalArgumentException("Training polling delay must be at least one second");
        this.execution=execution;
        this.registration=registration;
        this.executionDelay=executionDelay;
        this.registrationDelay=registrationDelay;
        executor=Executors.newScheduledThreadPool(registration==null?1:2,task->{
            var thread=new Thread(task,"training-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if(!started.compareAndSet(false,true))return;
        schedule(execution,executionDelay);
        if(registration!=null)schedule(registration,registrationDelay);
    }

    private void schedule(Runnable work,long delay) {
        executor.scheduleWithFixedDelay(()->{
            try {work.run();}
            catch(RuntimeException error) {log.warn("Training polling deferred after {}",error.getClass().getSimpleName());}
        },delay,delay,TimeUnit.MILLISECONDS);
    }

    @Override public void close() {
        executor.shutdownNow();
    }
}
