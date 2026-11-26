package com.aistock.market.quotation;

import com.aistock.common.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IndexSnapshotStore {
    public record Request(LocalDate start, LocalDate end) {}
    public record Day(LocalDate date, Boolean open) {}
    public record Point(LocalDate date, String close) {}
    public record Series(String indexId, String sourceCode, List<Point> points) {}
    public record Candidate(String provider, LocalDate start, LocalDate end, Instant fetchedAt,
                            String sourcePayloadHash, List<Day> calendar, List<Series> indices) {}
    public record IndexView(String indexId, String sourceCode, LocalDate date, String close,
                            LocalDate previousDate, String previousClose, String changePercent) {}
    public record Snapshot(long version, String provider, Instant fetchedAt, List<IndexView> indices) {}

    public record CoveredSnapshot(Snapshot snapshot, LocalDate checkedThrough) {}

    private static final List<String> IDS = List.of("SSE_COMPOSITE", "CSI_300");
    private static final List<String> SOURCE_CODES = List.of("sh.000001", "sh.000300");
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public IndexSnapshotStore(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    void checkRequest(Request request) {
        if (request == null || request.start() == null || request.end() == null
                || request.start().isBefore(LocalDate.of(1991, 1, 1))
                || request.end().isBefore(request.start())
                || request.end().isAfter(LocalDate.now(clock.withZone(SHANGHAI)))
                || ChronoUnit.DAYS.between(request.start(), request.end()) >= 31) {
            throw invalid();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Snapshot publish(Candidate candidate) {
        LocalDate latest = validate(candidate);
        jdbc.update("""
                insert into index_market_snapshot(range_start,range_end,trade_date,fetched_at,source_hash,candidate_json)
                values(?,?,?,?,?,?) on duplicate key update snapshot_id=snapshot_id
                """, candidate.start(), candidate.end(), latest, java.sql.Timestamp.from(candidate.fetchedAt()), candidate.sourcePayloadHash(), encode(candidate));
        return jdbc.queryForObject("""
                select snapshot_id,candidate_json from index_market_snapshot where source_hash=?
                """, (row, n) -> view(row.getLong(1), row.getString(2)), candidate.sourcePayloadHash());
    }

    public Optional<Snapshot> current() {
        return currentWithCoverage().map(CoveredSnapshot::snapshot);
    }

    public Optional<CoveredSnapshot> currentWithCoverage() {
        return jdbc.query("""
                select snapshot_id,candidate_json,range_end from index_market_snapshot
                order by trade_date desc,fetched_at desc,snapshot_id desc limit 1
                """, (row, n) -> new CoveredSnapshot(view(row.getLong(1), row.getString(2)),row.getObject(3,LocalDate.class))).stream().findFirst();
    }

    private LocalDate validate(Candidate candidate) {
        if (candidate == null) throw invalid();
        checkRequest(new Request(candidate.start(), candidate.end()));
        Instant now = clock.instant();
        if (!"baostock".equals(candidate.provider()) || candidate.fetchedAt() == null
                || candidate.fetchedAt().isBefore(now.minusSeconds(300))
                || candidate.fetchedAt().isAfter(now.plusSeconds(5))
                || candidate.calendar() == null || candidate.indices() == null
                || candidate.indices().size() != IDS.size()
                || candidate.calendar().size() != ChronoUnit.DAYS.between(candidate.start(), candidate.end()) + 1) {
            throw invalid();
        }
        LocalDate expected = candidate.start();
        var tradingDays = new java.util.ArrayList<LocalDate>();
        for (Day day : candidate.calendar()) {
            if (day == null || !expected.equals(day.date()) || day.open() == null) throw invalid();
            if (day.open()) tradingDays.add(day.date());
            expected = expected.plusDays(1);
        }
        if (tradingDays.size() < 2) throw invalid();
        for (int i = 0; i < IDS.size(); i++) validateSeries(candidate.indices().get(i), i, tradingDays);
        if (!fingerprint(candidate.calendar(), candidate.indices()).equals(candidate.sourcePayloadHash())) throw invalid();
        return tradingDays.get(tradingDays.size() - 1);
    }

    private void validateSeries(Series series, int index, List<LocalDate> tradingDays) {
        if (series == null || !IDS.get(index).equals(series.indexId())
                || !SOURCE_CODES.get(index).equals(series.sourceCode())
                || series.points() == null || series.points().size() != tradingDays.size()) throw invalid();
        for (int i = 0; i < tradingDays.size(); i++) {
            Point point = series.points().get(i);
            if (point == null || !tradingDays.get(i).equals(point.date()) || point.close() == null
                    || !point.close().matches("[0-9]{1,10}(\\.[0-9]{1,8})?")
                    || new BigDecimal(point.close()).signum() <= 0) throw invalid();
        }
    }

    // These records match the algorithm's sorted-key canonical JSON, independent of the storage serializer.
    static String fingerprint(List<Day> calendar, List<Series> indices) {
        var days = new StringJoiner(",");
        for (Day day : calendar) days.add("{\"date\":\"" + day.date() + "\",\"open\":" + day.open() + "}");
        var series = new StringJoiner(",");
        for (Series index : indices) {
            var points = new StringJoiner(",");
            for (Point point : index.points()) {
                points.add("{\"close\":\"" + point.close() + "\",\"date\":\"" + point.date() + "\"}");
            }
            series.add("{\"indexId\":\"" + index.indexId() + "\",\"points\":[" + points
                    + "],\"sourceCode\":\"" + index.sourceCode() + "\"}");
        }
        String canonical = "{\"calendar\":[" + days + "],\"indices\":[" + series + "]}";
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private String encode(Candidate candidate) {
        try {
            return mapper.writeValueAsString(candidate);
        } catch (JsonProcessingException error) {
            throw invalid();
        }
    }

    private Snapshot view(long version, String json) {
        try {
            Candidate candidate = mapper.readValue(json, Candidate.class);
            return new Snapshot(version, candidate.provider(), candidate.fetchedAt(),
                    candidate.indices().stream().map(IndexSnapshotStore::toIndexView).toList());
        } catch (JsonProcessingException error) {
            throw new BusinessException("INDEX_SNAPSHOT_UNAVAILABLE", "指数快照暂不可读", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private static IndexView toIndexView(Series series) {
        Point latest = series.points().get(series.points().size() - 1);
        Point previous = series.points().get(series.points().size() - 2);
        var prior = new BigDecimal(previous.close());
        var change = new BigDecimal(latest.close()).subtract(prior).movePointRight(2)
                .divide(prior, 4, RoundingMode.HALF_UP);
        return new IndexView(series.indexId(), series.sourceCode(), latest.date(), latest.close(),
                previous.date(), previous.close(), change.toPlainString());
    }

    static BusinessException invalid() {
        return new BusinessException("INDEX_SOURCE_INVALID", "指数数据未通过校验", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
