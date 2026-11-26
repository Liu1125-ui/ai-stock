package com.aistock.gateway;
/** Test adapter keeps package-private production bean factory visibility unchanged. */
public final class StatisticsCorsFixture {public static jakarta.servlet.Filter filter(){return new AdminTaskCorsConfiguration().adminTaskCors("http://127.0.0.1:15173").getFilter();}}
