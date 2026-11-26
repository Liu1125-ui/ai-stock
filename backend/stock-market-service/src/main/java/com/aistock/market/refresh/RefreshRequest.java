package com.aistock.market.refresh;
import com.aistock.common.adminrefresh.AdminMarketRefreshRecords.Constraint;
import java.time.*;
public record RefreshRequest(String operationId,String instrumentId,LocalDate targetDate,Instant leaseUntil,Constraint adminConstraint) {
    public RefreshRequest(String operationId,String instrumentId,LocalDate targetDate,Instant leaseUntil){this(operationId,instrumentId,targetDate,leaseUntil,null);}
}