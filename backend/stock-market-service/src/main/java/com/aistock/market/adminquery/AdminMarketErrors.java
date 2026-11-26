package com.aistock.market.adminquery;
import java.util.Set;
public final class AdminMarketErrors {
 private AdminMarketErrors() {}
 private static final Set<String> SAFE=Set.of("SOURCE_TIMEOUT","SOURCE_BUSY","SOURCE_UNAVAILABLE","SOURCE_INVALID","MARKET_DATA_INCOMPLETE","MARKET_DATA_INVALID","REFRESH_REQUEST_INVALID","REFRESH_RANGE_INVALID","REFRESH_OPERATION_CONFLICT","REFRESH_RUNNING","ATTEMPT_EXPIRED","MARKET_REVISION_CONFLICT","MARKET_COVERAGE_REGRESSION","MARKET_OPERATION_CONFLICT","MARKET_OPERATION_REJECTED","REAL_MARKET_VERSION_REQUIRED");
 public static String safe(String code) {return code==null?null:SAFE.contains(code)?code:"UNKNOWN_ERROR";}
}
