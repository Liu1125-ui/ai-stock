package com.aistock.common.statistics;
import com.aistock.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.Test;import org.springframework.util.*;
import static org.junit.jupiter.api.Assertions.*;
class StatisticsWindowTest {
 static final Clock CLOCK=Clock.fixed(Instant.parse("2026-10-21T16:00:00Z"),ZoneOffset.UTC);
 static LinkedMultiValueMap<String,String> query(String... values){var q=new LinkedMultiValueMap<String,String>();for(int i=0;i<values.length;i+=2)q.add(values[i],values[i+1]);return q;}
 @Test void shanghaiMidnightUsesHalfOpenUtcRange(){var w=StatisticsWindow.parse(query(),CLOCK);assertEquals(LocalDate.parse("2026-10-16"),w.from());assertEquals(LocalDate.parse("2026-10-22"),w.to());assertEquals(Instant.parse("2026-10-15T16:00:00Z"),w.startInclusive());assertEquals(Instant.parse("2026-10-22T16:00:00Z"),w.endExclusive());}
 @Test void requiresPairedDatesAndRejectsDuplicates(){for(var q:List.of(query("from","2026-10-20"),query("to","2026-10-20"),query("from","2026-10-20","to","2026-10-21","from","2026-10-20"),query("unused","x"))){var e=assertThrows(BusinessException.class,()->StatisticsWindow.parse(q,CLOCK));assertEquals("STATISTICS_QUERY_INVALID",e.code());assertEquals(400,e.status().value());}}
 @Test void rejectsInvalidCalendarAndOutsideWindow(){for(String[] values:List.of(new String[]{"2026-02-30","2026-10-22"},new String[]{"2026-10-23","2026-10-23"},new String[]{"2026-07-24","2026-10-22"},new String[]{"2026-10-22","2026-10-21"},new String[]{"2026-1-01","2026-01-02"},new String[]{"","2026-10-22"})){assertThrows(BusinessException.class,()->StatisticsWindow.parse(query("from",values[0],"to",values[1]),CLOCK));}}
 @Test void acceptsOneDayAndExactlyNinetyDays(){assertEquals(Instant.parse("2026-10-21T16:00:00Z"),StatisticsWindow.parse(query("from","2026-10-22","to","2026-10-22"),CLOCK).startInclusive());assertEquals(LocalDate.parse("2026-07-25"),StatisticsWindow.parse(query("from","2026-07-25","to","2026-10-22"),CLOCK).from());}
 @Test void marketExtrasAreExplicitAndStillRejectDuplicates(){var q=query("page","2","size","50");StatisticsQuery.window(q,CLOCK,Set.of("page","size"));assertEquals(2,StatisticsQuery.page(q));assertEquals(50,StatisticsQuery.size(q));q.add("page","2");assertThrows(BusinessException.class,()->StatisticsQuery.window(q,CLOCK,Set.of("page","size")));}
 @Test void boundedPaginationAndNoHttpParameters(){assertEquals(1,StatisticsQuery.page(query()));assertEquals(20,StatisticsQuery.size(query()));for(var q:List.of(query("page","0"),query("page","10001"),query("page","01"),query("size","51"),query("size","-1"))){assertThrows(BusinessException.class,()->{StatisticsQuery.page(q);StatisticsQuery.size(q);});}assertThrows(BusinessException.class,()->StatisticsQuery.none(query("from","2026-10-22")));StatisticsQuery.none(query());}
 @Test void metadataDoesNotLeakInternalUtcBoundaries()throws Exception{var node=new ObjectMapper().findAndRegisterModules().valueToTree(StatisticsMeta.of(StatisticsWindow.parse(query(),CLOCK),CLOCK.instant()));assertEquals("Asia/Shanghai",node.path("window").path("timeZone").asText());assertFalse(node.path("window").has("startInclusive"));assertFalse(node.path("window").has("endExclusive"));assertTrue(node.has("queriedAt"));}
}
