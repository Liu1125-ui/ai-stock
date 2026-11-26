package com.aistock.prediction.analysis;
import java.time.LocalDate;import java.util.UUID;
public record AnalysisInput(UUID operationId,String instrumentId,LocalDate from,LocalDate to,UUID marketVersionId,UUID predictionId) {
 public AnalysisInput(UUID operationId,String instrumentId,LocalDate from,LocalDate to,UUID marketVersionId){this(operationId,instrumentId,from,to,marketVersionId,null);}
}
