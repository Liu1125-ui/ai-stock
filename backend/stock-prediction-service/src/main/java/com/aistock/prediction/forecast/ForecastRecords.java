package com.aistock.prediction.forecast;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;import java.util.*;
public final class ForecastRecords {
 private ForecastRecords(){}
 public record Input(String operationId,String modelId,String instrumentId,Integer horizon){}
 public record Record(UUID predictionId,UUID operationId,UUID modelId,String instrumentId,int horizon,String state,String errorCode,JsonNode modelSnapshot,JsonNode snapshot,boolean hidden,Instant createdAt,Instant expiresAt){}
 public record Claim(Record record,boolean execute){}
 public record Page(List<Record> items,long total,int page,int size){}
}
