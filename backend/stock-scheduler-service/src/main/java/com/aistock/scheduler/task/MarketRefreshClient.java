package com.aistock.scheduler.task;
import com.aistock.common.ApiResponse;
import java.time.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
@FeignClient(name="stock-market-service",url="${aistock.market.url:}")
public interface MarketRefreshClient {
    @PostMapping("/internal/v1/market/refresh") ApiResponse<Result> refresh(@RequestBody Request body,@RequestHeader("X-Service-Token")String token);
    record Request(String operationId,String instrumentId,LocalDate targetDate,Instant leaseUntil,com.aistock.common.adminrefresh.AdminMarketRefreshRecords.Constraint adminConstraint) {
 public Request(String op,String instrument,LocalDate target,Instant lease){this(op,instrument,target,lease,null);}
 }
    record Result(String operationId,String state,String versionId,String errorCode) {}
}
