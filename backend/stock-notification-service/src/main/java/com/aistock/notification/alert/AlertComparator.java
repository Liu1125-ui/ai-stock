package com.aistock.notification.alert;
import java.math.BigDecimal;
/** Strict comparisons on exact prices; never round displayed percentages before comparison. */
public final class AlertComparator {
 private AlertComparator(){}
 public static boolean matches(String condition,BigDecimal threshold,BigDecimal raw,BigDecimal previous,BigDecimal current){
  if(condition==null||threshold==null||threshold.signum()<=0)throw AlertRemoteHttp.invalid();
  return switch(condition){case "PRICE_ABOVE" -> positive(raw).compareTo(threshold)>0;case "PRICE_BELOW" -> positive(raw).compareTo(threshold)<0;case "DAILY_RISE" -> positive(current).subtract(positive(previous)).multiply(new BigDecimal("100")).compareTo(threshold.multiply(previous))>0;case "DAILY_FALL" -> positive(current).subtract(positive(previous)).multiply(new BigDecimal("100")).compareTo(threshold.multiply(previous).negate())<0;default -> throw AlertRemoteHttp.invalid();};
 }
 private static BigDecimal positive(BigDecimal value){if(value==null||value.signum()<=0)throw AlertRemoteHttp.invalid();return value;}
}
