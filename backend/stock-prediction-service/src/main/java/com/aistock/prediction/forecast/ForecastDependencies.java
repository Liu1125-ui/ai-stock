package com.aistock.prediction.forecast;
import com.aistock.prediction.modelcatalog.ModelRecords.Version;
import com.fasterxml.jackson.databind.JsonNode;import java.util.UUID;
public interface ForecastDependencies {
 JsonNode prepare(String instrumentId,UUID operationId);
 JsonNode infer(Version model,JsonNode window);
}
