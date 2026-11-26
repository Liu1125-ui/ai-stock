package com.aistock.market.alert;
import java.time.LocalDate;
public interface AlertEvidenceCollector {
 AlertEvidenceRecords.Source collect(AlertEvidenceRecords.Request request,LocalDate previousTradeDate);
}
