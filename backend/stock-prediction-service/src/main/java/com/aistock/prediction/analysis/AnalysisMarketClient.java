package com.aistock.prediction.analysis;
import com.fasterxml.jackson.databind.JsonNode;import java.time.LocalDate;import java.util.UUID;
public interface AnalysisMarketClient {JsonNode evidence(String instrumentId,LocalDate from,LocalDate to,UUID versionId);}
