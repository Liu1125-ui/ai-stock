package com.aistock.prediction.verification;
import java.time.*;import java.util.UUID;
public record VerificationRequest(UUID operationId,LocalDate dueThrough,Instant leaseUntil){}
