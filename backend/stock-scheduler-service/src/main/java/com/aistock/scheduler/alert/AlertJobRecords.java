package com.aistock.scheduler.alert;
import java.time.*;import java.util.*;
public final class AlertJobRecords {
 private AlertJobRecords(){}
 public record Input(UUID ruleId,long revision,String instrumentId,LocalDate tradeDate,UUID rawVersionId,String roundId){}
 public record Job(UUID id,Input input,UUID evidenceId,String stage,String state,int attempts,Instant nextAt,String errorCode){}
 public record Claim(UUID jobId,UUID attemptId,String stage,Input input,UUID evidenceId,Instant leaseUntil){}
 public record Result(UUID operationId,String state,UUID referenceId,String errorCode){}
}
