package com.aistock.common.statistics;
import java.math.*;
/** JSON strings preserve large counters and decimal measurements. */
public final class StatisticsNumbers {
 private StatisticsNumbers(){}
 public static String count(long n){if(n<0)throw new IllegalArgumentException("Negative statistics count");return Long.toString(n);}
 public static String ratio(long numerator,long denominator){count(numerator);count(denominator);return denominator==0?null:BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(denominator),2,RoundingMode.HALF_UP).toPlainString();}
 public static String meanMillis(BigDecimal sumMillis,long samples){count(samples);if(sumMillis==null||sumMillis.signum()<0)throw new IllegalArgumentException("Invalid duration sum");return samples==0?null:sumMillis.divide(BigDecimal.valueOf(samples),3,RoundingMode.HALF_UP).toPlainString();}
}
