package com.aistock.market.calendar;
@FunctionalInterface public interface CalendarCollector {
 CalendarRecords.Candidate collect(CalendarRecords.Request request);
}
