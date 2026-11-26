package com.aistock.market.comparison;

import com.aistock.common.BusinessException;
import com.aistock.market.quotation.RawBar;
import com.aistock.market.quotation.TradingDay;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.http.HttpStatus;
import static com.aistock.market.comparison.ComparisonEvidenceRecords.*;

/** Verifies paired source evidence without filling missing sessions. */
public final class ComparisonEvidenceValidator {
    public static final String DEFINITION = "baostock-comparison-backward-close-v1";
    private final Clock clock;
    private final ObjectMapper mapper = new ObjectMapper();

    public ComparisonEvidenceValidator(Clock clock) { this.clock = Objects.requireNonNull(clock); }

    public Validated validate(Candidate candidate, List<RawBar> rawBars) {
        request(candidate);
        var calendar = calendar(candidate);
        var raw = new TreeMap<LocalDate, RawBar>();
        if (rawBars == null) throw invalid("COMPARISON_RAW_INVALID");
        for (var bar : rawBars) {
            if (bar == null || bar.date() == null || raw.put(bar.date(), bar) != null)
                throw invalid("COMPARISON_RAW_INVALID");
        }
        var points = new TreeMap<LocalDate, Point>();
        LocalDate previous = null;
        for (var point : candidate.points()) {
            if (point == null || point.date() == null
                || !Boolean.TRUE.equals(calendar.get(point.date()))
                || previous != null && !point.date().isAfter(previous)
                || !decimal(point.rawClose(), 20, 6, true)
                || !decimal(point.backwardClose(), 28, 12, true)
                || point.volumeShares() != null && !decimal(point.volumeShares(), 20, 4, false)
                || point.tradeStatus() != 0 && point.tradeStatus() != 1)
                throw invalid("COMPARISON_POINT_INVALID");
            var source = raw.get(point.date());
            if (source == null || !same(source.close(), point.rawClose())
                || !same(source.volumeShares(), point.volumeShares())
                || source.tradeStatus() != point.tradeStatus())
                throw invalid("COMPARISON_RAW_MISMATCH");
            points.put(point.date(), point);
            previous = point.date();
        }
        var days = new ArrayList<DayEvidence>();
        for (var entry : calendar.entrySet()) {
            if (!entry.getValue()) continue;
            var point = points.get(entry.getKey());
            if (point == null) {
                days.add(new DayEvidence(entry.getKey(), "MISSING", null, "MISSING", null));
            } else if (point.tradeStatus() == 0) {
                days.add(new DayEvidence(entry.getKey(), "SUSPENDED", null, "SUSPENDED", null));
            } else {
                days.add(new DayEvidence(entry.getKey(), "VERIFIED", number(point.backwardClose()),
                    point.volumeShares() == null ? "MISSING" : "VERIFIED", number(point.volumeShares())));
            }
        }
        var calendarRows = candidate.calendar().stream()
            .map(d -> List.of(d.date().toString(), d.open())).toList();
        var dayRows = days.stream().map(d -> Arrays.asList(d.date().toString(), d.priceState(),
            d.backwardClose(), d.volumeState(), d.volumeShares())).toList();
        var calendarHash = hash(calendarRows);
        var contentHash = hash(List.of(candidate.instrumentId(), candidate.rawVersionId().toString(),
            candidate.from().toString(), candidate.to().toString(), candidate.provider(),
            candidate.definition(), calendarHash, dayRows));
        return new Validated(candidate, List.copyOf(days), calendarHash, contentHash);
    }

    private void request(Candidate c) {
        if (c == null || c.operationId() == null || !c.operationId().matches("[A-Za-z0-9_-]{1,128}")
            || c.instrumentId() == null || !c.instrumentId().matches("(SH|SZ)[0-9]{6}")
            || c.rawVersionId() == null || !"baostock".equals(c.provider())
            || !DEFINITION.equals(c.definition()) || c.from() == null || c.to() == null
            || c.from().isBefore(LocalDate.of(1990,1,1)) || c.from().isAfter(c.to())
            || c.from().isBefore(c.to().minusYears(1))
            || c.to().isAfter(clock.instant().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate())
            || c.fetchedAt() == null || c.fetchedAt().isAfter(clock.instant().plusSeconds(300))
            || c.fetchedAt().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().isBefore(c.to())
            || c.sourcePayloadHash() == null || !c.sourcePayloadHash().matches("[a-f0-9]{64}")
            || c.points() == null || c.points().size() > 370
            || c.calendar() == null || c.calendar().isEmpty() || c.calendar().size() > 367)
            throw invalid("COMPARISON_REQUEST_INVALID");
    }

    private TreeMap<LocalDate, Boolean> calendar(Candidate c) {
        var result = new TreeMap<LocalDate, Boolean>();
        var expected = c.from();
        for (TradingDay day : c.calendar()) {
            if (day == null || !expected.equals(day.date()) || day.date().isAfter(c.to()))
                throw invalid("COMPARISON_CALENDAR_INVALID");
            result.put(day.date(), day.open());
            expected = expected.plusDays(1);
        }
        if (!expected.equals(c.to().plusDays(1))) throw invalid("COMPARISON_CALENDAR_INVALID");
        return result;
    }

    private boolean decimal(BigDecimal value, int precision, int scale, boolean positive) {
        if (value == null || (positive ? value.signum() <= 0 : value.signum() < 0)) return false;
        var n = value.stripTrailingZeros();
        return n.scale() <= scale && (long)n.precision() - n.scale() <= precision - scale;
    }
    private boolean same(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }
    private String number(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }
    private String hash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(mapper.writeValueAsBytes(value)));
        } catch (Exception error) {
            throw new BusinessException("COMPARISON_SERIALIZATION_FAILED",
                "股票对比证据暂不可用", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    static BusinessException invalid(String code) {
        return new BusinessException(code, "股票对比历史证据不满足校验要求", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}