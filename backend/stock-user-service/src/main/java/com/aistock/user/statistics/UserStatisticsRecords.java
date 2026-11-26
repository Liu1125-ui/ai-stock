package com.aistock.user.statistics;
import com.aistock.common.statistics.StatisticsMeta;import static com.aistock.common.statistics.StatisticsRecords.*;import java.time.LocalDate;import java.util.List;
public final class UserStatisticsRecords {
 private UserStatisticsRecords(){}
 public record LoginDay(LocalDate date,String successfulLogins,String distinctUsers){}
 public record UsersSummary(StatisticsMeta meta,String totalUsers,String enabledUsers,String disabledUsers,List<CountByKey> roleCounts,String registrations,List<DailyCount> registrationDaily,String successfulLogins,String distinctLoginUsers,List<LoginDay> loginDaily){public UsersSummary{roleCounts=List.copyOf(roleCounts);registrationDaily=List.copyOf(registrationDaily);loginDaily=List.copyOf(loginDaily);}}
}
