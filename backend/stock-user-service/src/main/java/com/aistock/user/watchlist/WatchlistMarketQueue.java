package com.aistock.user.watchlist;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WatchlistMarketQueue {
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;

    public WatchlistMarketQueue(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    void enqueue(String instrument, Instant now) {
        var target=targetDate(now);
        jdbc.update("""
            INSERT INTO watchlist_market_queue(instrument_id,target_date,next_attempt_at) VALUES (?,?,?)
            ON DUPLICATE KEY UPDATE
                delivered=IF(VALUES(target_date)>target_date,FALSE,delivered),
                next_attempt_at=IF(VALUES(target_date)>target_date,VALUES(next_attempt_at),next_attempt_at),
                target_date=GREATEST(target_date,VALUES(target_date))
            """,instrument,target,Timestamp.from(now));
    }

    void enqueueActive(Instant now, int limit) {
        var stocks=jdbc.queryForList("""
            SELECT DISTINCT w.instrument_id
            FROM watchlist w JOIN user_account a ON a.user_id=w.user_id AND a.status='ENABLED'
            LEFT JOIN watchlist_market_queue q ON q.instrument_id=w.instrument_id
            WHERE q.instrument_id IS NULL OR q.target_date<?
            ORDER BY w.instrument_id LIMIT ?
            """,String.class,targetDate(now),limit);
        for(var instrument:stocks)enqueue(instrument,now);
    }

    private LocalDate targetDate(Instant now) {
        var local=now.atZone(MARKET_ZONE);
        return local.toLocalDate().minusDays(local.getHour()<20 ? 1 : 0);
    }

    List<Request> due(Instant now, int limit) {
        return jdbc.query("""
            SELECT q.instrument_id,q.target_date FROM watchlist_market_queue q
            WHERE q.delivered=FALSE AND q.next_attempt_at<=? AND EXISTS (
                SELECT 1 FROM watchlist w JOIN user_account a ON a.user_id=w.user_id
                WHERE w.instrument_id=q.instrument_id AND a.status='ENABLED'
            )
            ORDER BY q.next_attempt_at,q.instrument_id LIMIT ?
            """,(rs,n)->new Request(rs.getString(1),rs.getDate(2).toLocalDate()),Timestamp.from(now),limit);
    }

    void delivered(Request request) {
        jdbc.update("UPDATE watchlist_market_queue SET delivered=TRUE WHERE instrument_id=? AND target_date=?",
            request.instrumentId(),request.targetDate());
    }

    void defer(Request request, Instant next) {
        jdbc.update("""
            UPDATE watchlist_market_queue SET next_attempt_at=?
            WHERE instrument_id=? AND target_date=? AND delivered=FALSE
            """,Timestamp.from(next),request.instrumentId(),request.targetDate());
    }

    record Request(String instrumentId, LocalDate targetDate) {
        String key() { return "daily-"+instrumentId+"-"+targetDate; }
    }
}
