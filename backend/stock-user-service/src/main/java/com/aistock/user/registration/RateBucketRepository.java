package com.aistock.user.registration;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;

public final class RateBucketRepository {
    private final JdbcTemplate jdbc;
    public RateBucketRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Bucket(String key, Instant startedAt, Instant endsAt, int count, Instant lastAt) { }
    public Bucket lock(String key, String scope, Instant now, long windowSeconds) {
        jdbc.update("INSERT INTO auth_rate_bucket(bucket_key,scope,window_started_at,window_ends_at,request_count,revision) VALUES (?,?,?,?,0,0) ON DUPLICATE KEY UPDATE bucket_key=bucket_key", key, scope, Timestamp.from(now), Timestamp.from(now.plusSeconds(windowSeconds)));
        return jdbc.queryForObject("SELECT * FROM auth_rate_bucket WHERE bucket_key=? FOR UPDATE", (rs, row) -> {
            Timestamp last = rs.getTimestamp("last_request_at");
            return new Bucket(key, rs.getTimestamp("window_started_at").toInstant(), rs.getTimestamp("window_ends_at").toInstant(), rs.getInt("request_count"), last == null ? null : last.toInstant());
        }, key);
    }
    public void write(String key, Instant start, Instant end, int count, Instant last) {
        jdbc.update("UPDATE auth_rate_bucket SET window_started_at=?,window_ends_at=?,request_count=?,last_request_at=?,revision=revision+1 WHERE bucket_key=?", Timestamp.from(start), Timestamp.from(end), count, Timestamp.from(last), key);
    }
}
