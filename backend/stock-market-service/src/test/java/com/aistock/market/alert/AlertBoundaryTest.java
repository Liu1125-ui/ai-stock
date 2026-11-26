package com.aistock.market.alert;
import com.aistock.market.quotation.*;import com.aistock.common.BusinessException;import org.junit.jupiter.api.Test;import java.time.*;import java.util.*;import java.math.BigDecimal;import static org.junit.jupiter.api.Assertions.*;
class AlertBoundaryTest {
 private RawBar bar(String day,int status){var v=new BigDecimal("25");return new RawBar(LocalDate.parse(day),v,v,v,v,v,v,status,null);}
 private List<TradingDay> days(){return java.util.stream.IntStream.rangeClosed(8,12).mapToObj(d->new TradingDay(LocalDate.of(2026,10,d),d!=10&&d!=11)).toList();}
 private AlertBoundaryService.Boundary boundary(String now,List<RawBar> bars,List<TradingDay> cal){return AlertBoundaryService.resolve("SH600030",UUID.fromString("2d8890da-4c38-4970-baa2-cef4e4c3df6b"),bars,cal,Instant.parse(now),20);}
 @Test void weekendUsesLastCompletedTradingDay(){var b=boundary("2026-10-10T02:00:00Z",List.of(bar("2026-10-09",1)),days());assertEquals(LocalDate.of(2026,10,9),b.effectiveAfterTradeDate());}
 @Test void staleRawDoesNotBackdateNewRule(){var b=boundary("2026-10-09T13:00:00Z",List.of(bar("2026-10-08",1)),days());assertEquals(LocalDate.of(2026,10,9),b.expectedCompletedTradeDate());assertEquals(LocalDate.of(2026,10,9),b.effectiveAfterTradeDate());}
 @Test void beforeCutoffStillExcludesAlreadyPublishedToday(){var b=boundary("2026-10-09T10:00:00Z",List.of(bar("2026-10-09",1)),days());assertEquals(LocalDate.of(2026,10,8),b.expectedCompletedTradeDate());assertEquals(LocalDate.of(2026,10,9),b.effectiveAfterTradeDate());}
 @Test void missingCalendarRejectsRatherThanGuess(){assertThrows(BusinessException.class,()->boundary("2026-10-10T02:00:00Z",List.of(bar("2026-10-09",1)),days().stream().filter(d->d.date().getDayOfMonth()!=9).toList()));}
 @Test void suspendedOrFutureRawCannotDefineBoundary(){assertThrows(BusinessException.class,()->boundary("2026-10-10T02:00:00Z",List.of(bar("2026-10-09",0)),days()));assertThrows(BusinessException.class,()->boundary("2026-10-10T02:00:00Z",List.of(bar("2026-10-12",1)),days()));}
}
