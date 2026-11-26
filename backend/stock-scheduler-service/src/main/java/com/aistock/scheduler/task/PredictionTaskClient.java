package com.aistock.scheduler.task;
import java.time.*;import java.util.*;
public interface PredictionTaskClient {
 MarketRefreshClient.Result execute(TaskRepository.Claim claim);
 MarketRefreshClient.Result receipt(TaskRepository.Claim claim);
 Page due(LocalDate cutoff,String after);
 record Item(String predictionId,String instrumentId,LocalDate dueThrough,String ruleVersion){}
 record Page(LocalDate cutoffDate,List<Item> items,String nextCursor){public Page{items=List.copyOf(items);}}
 class MissingReceipt extends RuntimeException {public MissingReceipt(){super("Verification receipt not found");}}
}
