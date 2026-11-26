package com.aistock.scheduler.alert;

import java.time.*;
import java.util.*;

/** Only service interfaces are used; MS-04 never reads another service's database. */
public interface AlertMarketClient {
 CalendarSnapshot calendar();
 CalendarSnapshot refresh(UUID operationId,LocalDate start,LocalDate end);
 Boundary boundary(String instrument);
 Page due(String instrument,String after,int limit);
 record Day(LocalDate date,Boolean open){}
 record CalendarSnapshot(UUID versionId,String provider,LocalDate start,LocalDate end,Instant fetchedAt,String sourcePayloadHash,List<Day> days){public CalendarSnapshot{days=List.copyOf(days);}}
 record Boundary(String instrumentId,UUID rawVersionId,LocalDate latestPublishedTradeDate,LocalDate expectedCompletedTradeDate,Instant asOf){}
 record RuleRef(UUID ruleId,UUID ownerId,String instrumentId,long revision,LocalDate effectiveAfterTradeDate){}
 record Page(List<RuleRef> items,String nextCursor){public Page{items=List.copyOf(items);}}
}
