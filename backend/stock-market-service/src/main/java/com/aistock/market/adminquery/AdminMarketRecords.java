package com.aistock.market.adminquery;

import com.aistock.common.adminmarket.AdminMarketQuery.ListFilter;
import java.time.*;
import java.math.BigDecimal;
import java.util.List;

public final class AdminMarketRecords {
    public static final String DEFINITION="admin-market-readonly-v1";
    private AdminMarketRecords() {}
    public record HeadPage(Instant queriedAt,String definitionVersion,ListFilter query,List<HeadRow> items,boolean hasMore) {}
    public record HeadRow(String instrumentId,String ticker,String displayName,boolean nameVerified,String listingStatus,String provider,String priceBasis,boolean headPresent,String headState,String headVersionId,String headRevision,String versionStatus,String quality,LocalDate coverageStart,LocalDate coverageEnd,Instant checkedAt,Instant fetchedAt,Instant publishedAt,String lastErrorCode) {}
    public record VersionSummary(String versionId,String provider,String priceBasis,String status,String quality,LocalDate coverageStart,LocalDate coverageEnd,Instant fetchedAt,Instant publishedAt,boolean isCurrent) {}
    public record VersionPage(Instant queriedAt,String definitionVersion,String instrumentId,String provider,List<VersionSummary> items,String nextCursor,List<IngestionSummary> recentIngestions,List<RefreshSummary> recentRefreshes,boolean ingestionsTruncated,boolean refreshesTruncated) {}
    public record IngestionSummary(String operationId,String state,String versionId,String errorCode,Instant createdAt,Instant finishedAt) {}
    public record RefreshSummary(String operationId,String state,LocalDate targetDate,String versionId,String errorCode,Instant createdAt,Instant finishedAt) {}
    public record VersionMetadata(String versionId,String provider,String priceBasis,String status,String quality,LocalDate coverageStart,LocalDate coverageEnd,Instant fetchedAt,Instant publishedAt,boolean isCurrent,String contentHash,String sourcePayloadHash,String headRevision) {}
    public record Window(LocalDate from,LocalDate to,String timeZone) {}
    public record VersionDetail(Instant queriedAt,String definitionVersion,String instrumentId,VersionMetadata version,Window window,Diagnostics diagnostics) {}
    public record VersionBounds(LocalDate from,LocalDate to) {}
    public record DiagnosticBar(LocalDate date,BigDecimal open,BigDecimal high,BigDecimal low,BigDecimal close,BigDecimal volumeShares,BigDecimal amountYuan,int tradeStatus) {}
    public record CalendarDay(LocalDate date,boolean open) {}
    public record DateFinding(String count,List<LocalDate> sampleDates,boolean truncated) {}
    public record Diagnostics(String state,String calendarEvidence,LocalDate effectiveFrom,LocalDate effectiveTo,String outsideCoverageDays,String barDays,String tradedRowDays,String suspendedRowDays,String knownOpenDays,String unknownCalendarDays,DateFinding suspectedMissingOpenDays,DateFinding barsOnClosedDays,DateFinding invalidRowDays) {}
}
