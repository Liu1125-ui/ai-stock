package com.aistock.market.comparison;

import com.aistock.common.BusinessException;
import com.aistock.market.quotation.RawBar;
import com.aistock.market.quotation.TradingDay;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.aistock.market.comparison.ComparisonEvidenceRecords.*;

class ComparisonEvidenceValidatorTest {
    private final LocalDate day = LocalDate.of(2026, 10, 8);
    private final UUID rawId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-12T00:00:00Z"), ZoneOffset.UTC);
    private final ComparisonEvidenceValidator validator = new ComparisonEvidenceValidator(clock);
    private BigDecimal n(String value) { return value == null ? null : new BigDecimal(value); }
    private Point point(LocalDate date, String volume, int status) {
        return new Point(date, n("10"), n("20"), n(volume), status);
    }
    private RawBar raw(Point p) {
        return new RawBar(p.date(), n("10"), n("10"), n("10"), p.rawClose(),
            p.volumeShares(), n("1000"), p.tradeStatus(), null);
    }
    private Candidate candidate(List<Point> points, List<TradingDay> calendar) {
        return new Candidate("comparison-test", "SH600030", rawId, day, day.plusDays(1),
            "baostock", "baostock-comparison-backward-close-v1", clock.instant(),
            "a".repeat(64), points, calendar);
    }
    private List<TradingDay> calendar() {
        return List.of(new TradingDay(day, true), new TradingDay(day.plusDays(1), true));
    }
    private Validated validate(List<Point> points) {
        return validator.validate(candidate(points, calendar()), points.stream().map(this::raw).toList());
    }
    @Test void validatesAndKeepsIndependentVolumeMissingness() {
        var result = validate(List.of(point(day, null, 1), point(day.plusDays(1), "100", 1)));
        assertEquals("VERIFIED", result.days().get(0).priceState());
        assertEquals("MISSING", result.days().get(0).volumeState());
        assertNull(result.days().get(0).volumeShares());
        assertEquals("20", result.days().get(0).backwardClose());
        assertEquals(64, result.contentHash().length());
    }
    @Test void absentOpenDateIsExplicitMissing() {
        var result = validate(List.of(point(day, "100", 1)));
        assertEquals(2, result.days().size());
        assertEquals("MISSING", result.days().get(1).priceState());
        assertNull(result.days().get(1).backwardClose());
    }
    @Test void suspendedSourceIsNotAnEffectiveObservation() {
        var result = validate(List.of(point(day, "0", 0)));
        assertEquals("SUSPENDED", result.days().get(0).priceState());
        assertEquals("SUSPENDED", result.days().get(0).volumeState());
        assertNull(result.days().get(0).backwardClose());
    }
    @Test void rejectsWrongRawClose() {
        var p = point(day, "100", 1);
        var other = new RawBar(day,n("11"),n("11"),n("11"),n("11"),n("100"),n("1100"),1,null);
        assertThrows(BusinessException.class, () -> validator.validate(candidate(List.of(p), calendar()),List.of(other)));
    }
    @Test void rejectsMissingRawPair() {
        assertThrows(BusinessException.class, () -> validator.validate(candidate(List.of(point(day,"100",1)), calendar()),List.of()));
    }
    @Test void cannotHideKnownRawVolume() {
        assertThrows(BusinessException.class, () -> validator.validate(candidate(List.of(point(day,null,1)),calendar()),List.of(raw(point(day,"100",1)))));
    }
    @Test void rejectsDuplicateAndUnsortedPoints() {
        assertThrows(BusinessException.class, () -> validate(List.of(point(day,"100",1),point(day,"100",1))));
        assertThrows(BusinessException.class, () -> validate(List.of(point(day.plusDays(1),"100",1),point(day,"100",1))));
    }
    @Test void calendarMustBeContiguousAndWithinDeclaredRange() {
        var p = point(day,"100",1);
        assertThrows(BusinessException.class, () -> validator.validate(candidate(List.of(p),List.of(new TradingDay(day,true))),List.of(raw(p))));
    }
    @Test void closedDateCannotContainBar() {
        var p = point(day,"100",1);
        var c = List.of(new TradingDay(day,false),new TradingDay(day.plusDays(1),true));
        assertThrows(BusinessException.class, () -> validator.validate(candidate(List.of(p),c),List.of(raw(p))));
    }
    @Test void rejectsNonpositivePricesAndNegativeVolume() {
        var p = new Point(day,n("10"),n("0"),n("100"),1);
        assertThrows(BusinessException.class, () -> validate(List.of(p)));
        assertThrows(BusinessException.class, () -> validate(List.of(point(day,"-1",1))));
    }
    @Test void rejectsExcessPrecisionAndUnknownStatus() {
        assertThrows(BusinessException.class, () -> validate(List.of(new Point(day,n("10"),n("1.0000000000001"),n("100"),1))));
        assertThrows(BusinessException.class, () -> validate(List.of(point(day,"100",2))));
    }
    @Test void canonicalHashIgnoresDecimalScaleButIncludesRawIdentity() {
        var p = point(day,"100",1);
        var a = validate(List.of(p));
        var scaled = new Point(day,n("10.00"),n("20.000"),n("100.0000"),1);
        var b = validate(List.of(scaled));
        assertEquals(a.contentHash(),b.contentHash());
        var c = candidate(List.of(p),calendar());
        var changed = new Candidate(c.operationId(),c.instrumentId(),UUID.randomUUID(),c.from(),c.to(),
            c.provider(),c.definition(),c.fetchedAt(),c.sourcePayloadHash(),c.points(),c.calendar());
        assertNotEquals(a.contentHash(),validator.validate(changed,List.of(raw(p))).contentHash());
    }
    @Test void rejectsNullRequestAndFutureFetch() {
        assertThrows(BusinessException.class, () -> validator.validate(null,List.of()));
        var c = candidate(List.of(),calendar());
        var future = new Candidate(c.operationId(),c.instrumentId(),c.rawVersionId(),c.from(),c.to(),
            c.provider(),c.definition(),clock.instant().plusSeconds(301),c.sourcePayloadHash(),c.points(),c.calendar());
        assertThrows(BusinessException.class, () -> validator.validate(future,List.of()));
    }
    @Test void calendarYearLimitHandlesLeapDay() {
        var end=LocalDate.of(2024,2,29);
        var start=end.minusYears(1);
        var days=new ArrayList<TradingDay>();
        for(var d=start;!d.isAfter(end);d=d.plusDays(1))days.add(new TradingDay(d,false));
        var c=new Candidate("leap","SH600030",rawId,start,end,"baostock",
            "baostock-comparison-backward-close-v1",clock.instant(),"a".repeat(64),List.of(),days);
        assertEquals(0,validator.validate(c,List.of()).days().size());
        var over=new Candidate(c.operationId(),c.instrumentId(),rawId,start.minusDays(1),end,c.provider(),
            c.definition(),c.fetchedAt(),c.sourcePayloadHash(),c.points(),c.calendar());
        assertThrows(BusinessException.class, () -> validator.validate(over,List.of()));
    }
}