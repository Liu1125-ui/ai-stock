package com.aistock.common.statistics;
import java.time.*;
public record StatisticsMeta(Window window,Instant queriedAt,String definitionVersion){
 public record Window(LocalDate from,LocalDate to,String timeZone){}
 public static StatisticsMeta of(StatisticsWindow w,Instant now){return new StatisticsMeta(new Window(w.from(),w.to(),"Asia/Shanghai"),now,"admin-statistics-v1");}
}
