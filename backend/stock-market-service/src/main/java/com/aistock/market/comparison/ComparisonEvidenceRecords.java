package com.aistock.market.comparison;

import com.aistock.market.quotation.TradingDay;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Internal evidence contracts; prices are not model predictions. */
public final class ComparisonEvidenceRecords {
    private ComparisonEvidenceRecords() {}
    public record Candidate(String operationId, String instrumentId, UUID rawVersionId,
        LocalDate from, LocalDate to, String provider, String definition, Instant fetchedAt,
        String sourcePayloadHash, List<Point> points, List<TradingDay> calendar) {}
    public record Point(LocalDate date, BigDecimal rawClose, BigDecimal backwardClose,
        BigDecimal volumeShares, int tradeStatus) {}
    public record DayEvidence(LocalDate date, String priceState, String backwardClose,
        String volumeState, String volumeShares) {}
    public record Publication(UUID evidenceId, String contentHash, boolean replayed) {}
    public record Evidence(UUID evidenceId, String instrumentId, UUID rawVersionId,
        String rawContentHash, String provider, String basis, LocalDate from, LocalDate to,
        String calendarHash, String contentHash, String sourcePayloadHash, Instant capturedAt,
        List<DayEvidence> days, List<TradingDay> calendar) {}
    public record Validated(Candidate candidate, List<DayEvidence> days,
        String calendarHash, String contentHash) {}
}