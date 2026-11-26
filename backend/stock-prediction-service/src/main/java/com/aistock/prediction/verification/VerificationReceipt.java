package com.aistock.prediction.verification;
import java.util.UUID;
public record VerificationReceipt(UUID operationId,String state,String versionId,String errorCode){}
