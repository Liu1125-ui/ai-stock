package com.aistock.market.calendar;
import java.time.*;import java.util.*;
public final class CalendarRecords {
 private CalendarRecords(){}
 public record Request(String operationId,LocalDate start,LocalDate end){}
 public record Day(LocalDate date,Boolean open){}
 public record Candidate(String provider,LocalDate start,LocalDate end,Instant fetchedAt,String sourcePayloadHash,List<Day> days){}
 public record Snapshot(UUID versionId,Candidate candidate){}
}
