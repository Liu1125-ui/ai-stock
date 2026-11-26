package com.aistock.prediction.verification;
import com.fasterxml.jackson.databind.JsonNode;
@FunctionalInterface public interface VerificationMarketClient {JsonNode prepare(VerificationRequest request,JsonNode predictionSnapshot); default JsonNode prepare(VerificationRequest r,JsonNode snapshot,java.time.Instant deadline){return prepare(r,snapshot);}
}
