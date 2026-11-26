package com.aistock.market.quotation;

import com.aistock.common.ApiResponse;
import com.aistock.common.BusinessException;
import com.aistock.common.TraceIds;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class PilotLiveQuote {
    private static final Set<String> FEED_STATES = Set.of("OPEN", "CLOSED", "WAITING", "DISCONNECTED");

    public record Update(String feedState, String currency, BigDecimal price, Instant quoteTime) {}
    public record Snapshot(String state, String price, String currency, Instant quoteTime, Instant receivedAt) {}

    private final Clock clock;
    private final String token;
    private final int quoteMaxAge;
    private final int heartbeatMaxAge;
    private String feedState = "WAITING";
    private Instant lastHeartbeat;
    private Instant quoteTime;
    private Instant receivedAt;
    private BigDecimal price;

    public PilotLiveQuote(Clock clock,
            @Value("${aistock.market.live-pilot-token:}") String token,
            @Value("${aistock.market.live-quote-max-age:30}") int quoteMaxAge,
            @Value("${aistock.market.live-heartbeat-max-age:20}") int heartbeatMaxAge) {
        this.clock = clock;
        this.token = token;
        this.quoteMaxAge = quoteMaxAge;
        this.heartbeatMaxAge = heartbeatMaxAge;
    }

    @GetMapping("/api/v1/instruments/HK00700/realtime")
    public synchronized ResponseEntity<ApiResponse<Snapshot>> read(
            @RequestHeader(value = "X-Trace-Id", required = false) String trace) {
        var now = clock.instant();
        String state;
        if (token.isBlank()) state = "DISABLED";
        else if (lastHeartbeat == null) state = "WAITING";
        else if (lastHeartbeat.isBefore(now.minusSeconds(heartbeatMaxAge)) || "DISCONNECTED".equals(feedState))
            state = "DISCONNECTED";
        else if (!"OPEN".equals(feedState)) state = feedState;
        else if (quoteTime == null) state = "WAITING";
        else state = quoteTime.isBefore(now.minusSeconds(quoteMaxAge)) ? "STALE" : "LIVE";
        var snapshot = new Snapshot(state, price == null ? null : price.toPlainString(),
                "HKD", quoteTime, receivedAt);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(snapshot, TraceIds.normalize(trace)));
    }

    @PostMapping("/internal/v1/realtime/tencent")
    public synchronized ApiResponse<Void> update(
            @RequestHeader(value = "X-Quote-Token", required = false) String suppliedToken,
            @RequestHeader(value = "X-Trace-Id", required = false) String trace,
            @RequestBody Update input) {
        if (token.isBlank() || suppliedToken == null || !MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8), suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException("QUOTE_ACCESS_DENIED", "行情采集身份无效", HttpStatus.FORBIDDEN);
        }
        var now = clock.instant();
        boolean hasQuote = input.price() != null || input.currency() != null || input.quoteTime() != null;
        if (input.feedState() == null || !FEED_STATES.contains(input.feedState())
                || hasQuote && !validQuote(input, now)) {
            throw new BusinessException("VALIDATION_FAILED", "盘中报价参数不正确", HttpStatus.BAD_REQUEST);
        }
        lastHeartbeat = now;
        feedState = input.feedState();
        if (hasQuote && (quoteTime == null || !input.quoteTime().isBefore(quoteTime))) {
            if (quoteTime == null || !input.quoteTime().equals(quoteTime) || price.compareTo(input.price()) != 0) {
                price = input.price();
                quoteTime = input.quoteTime();
                receivedAt = now;
            }
        }
        return ApiResponse.success(null, TraceIds.normalize(trace));
    }

    private boolean validQuote(Update input, Instant now) {
        return !"DISCONNECTED".equals(input.feedState()) && "HKD".equals(input.currency())
                && input.price() != null && input.price().scale() >= 0 && input.price().scale() <= 6
                && input.price().signum() > 0 && input.price().precision() <= 12
                && input.quoteTime() != null && !input.quoteTime().isAfter(now.plusSeconds(5));
    }
}
