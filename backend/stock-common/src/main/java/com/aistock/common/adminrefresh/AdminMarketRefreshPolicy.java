package com.aistock.common.adminrefresh;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import static com.aistock.common.adminrefresh.AdminMarketRefreshRecords.*;

/** Date and cooldown rules only; admission still needs database locks and role checks. */
public final class AdminMarketRefreshPolicy {
    public static final String VERSION = "admin-market-refresh-v1";
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDate EARLIEST = LocalDate.of(1990, 1, 1);
    private final int cutoffHour;
    private final int maxDays;
    private final long cooldownSeconds;

    public AdminMarketRefreshPolicy() { this(20, 1500, 60); }
    public AdminMarketRefreshPolicy(int cutoffHour, int maxDays, long cooldownSeconds) {
        if (cutoffHour < 0 || cutoffHour > 23 || maxDays < 1 || cooldownSeconds < 1
            || cooldownSeconds > 86400) throw new IllegalArgumentException("Invalid refresh policy");
        this.cutoffHour = cutoffHour;
        this.maxDays = maxDays;
        this.cooldownSeconds = cooldownSeconds;
    }

    public DateWindow window(Context context, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        if (!valid(context)) return new DateWindow(null, null, null);
        var now = clock.instant().atZone(ZONE);
        LocalDate cutoff = now.getHour() < cutoffHour ? now.toLocalDate().minusDays(1) : now.toLocalDate();
        LocalDate max = context.coverageStart().plusDays(maxDays);
        if (cutoff.isBefore(max)) max = cutoff;
        if (context.coverageEnd().isAfter(max)) return new DateWindow(null, null, null);
        return new DateWindow(context.coverageEnd(), max, max);
    }

    public Decision admit(Context context, LocalDate target, Instant lastAdmittedAt, Clock clock) {
        DateWindow range = window(context, clock);
        if (!valid(context)) return new Decision(false, "REFRESH_CONTEXT_CHANGED", null);
        if (target == null || range.min() == null || target.isBefore(range.min()) || target.isAfter(range.max()))
            return new Decision(false, "REFRESH_RANGE_UNSUPPORTED", null);
        if (lastAdmittedAt != null) {
            Instant until;
            try { until = lastAdmittedAt.plusSeconds(cooldownSeconds); }
            catch (DateTimeException | ArithmeticException e) {
                return new Decision(false, "REFRESH_CONTEXT_CHANGED", null);
            }
            if (clock.instant().isBefore(until)) return new Decision(false, "REFRESH_COOLDOWN", until);
        }
        return new Decision(true, null, null);
    }

    private boolean valid(Context context) {
        if (context == null || !context.eligible() || !"baostock".equals(context.provider())
            || !"RAW".equals(context.priceBasis()) || context.coverageStart() == null
            || context.coverageEnd() == null || context.queriedAt() == null
            || context.coverageStart().isBefore(EARLIEST)
            || context.coverageEnd().isBefore(context.coverageStart())) return false;
        try {
            AdminMarketRefreshInput.instrument(context.instrumentId());
            AdminMarketRefreshInput.id(context.baseVersionId());
            AdminMarketRefreshInput.revision(context.headRevision());
            return true;
        } catch (com.aistock.common.BusinessException e) { return false; }
    }
}