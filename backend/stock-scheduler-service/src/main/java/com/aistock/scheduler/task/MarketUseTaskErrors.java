package com.aistock.scheduler.task;
import java.util.Set;
/** Only explicit, safely identified admission denials are terminal; transport uncertainty is not. */
public final class MarketUseTaskErrors {
 private MarketUseTaskErrors(){}
 private static final Set<String> DENIED=Set.of("MARKET_VERSION_USE_BLOCKED","MARKET_VERSION_UNAVAILABLE","MARKET_USE_ALREADY_RELEASED","MARKET_USE_BINDING_MISMATCH","MARKET_USE_NOT_READY");
 public static boolean definitive(int status,String code){return code!=null&&(status==409&&DENIED.contains(code)||status==503&&"MARKET_USE_NOT_READY".equals(code));}
}
