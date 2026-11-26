package com.aistock.scheduler.task;
import java.time.LocalDate;
public interface RevisionClient {
 Event next(long after);PredictionTaskClient.Page affected(String instrument,LocalDate cutoff,String after);
 record Event(long eventId,String instrumentId,String rawVersionId){}
}
