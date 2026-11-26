package com.aistock.scheduler.alert;
import static com.aistock.scheduler.alert.AlertJobRecords.*;
/** Remote operations are idempotent by attemptId; no owner or source data is supplied by callers. */
public interface AlertTaskClient {
 Result prepare(Claim claim);Result evaluate(Claim claim);Result receipt(Claim claim);
 final class MissingReceipt extends RuntimeException {public MissingReceipt(){super("Alert operation receipt not found");}}
}
