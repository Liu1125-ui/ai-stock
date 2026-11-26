package com.aistock.scheduler.task;
import java.util.List;
public interface ComparisonTaskClient {
 ComparisonTaskRecords.Options options(List<String> instruments);
 MarketRefreshClient.Result execute(TaskRepository.Claim claim,ComparisonTaskRecords.Context context);
 MarketRefreshClient.Result receipt(TaskRepository.Claim claim);
 final class MissingReceipt extends RuntimeException {}
}
