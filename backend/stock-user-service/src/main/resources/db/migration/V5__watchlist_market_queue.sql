CREATE TABLE watchlist_market_queue (
    instrument_id VARCHAR(16) PRIMARY KEY,
    target_date DATE NOT NULL,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    delivered BOOLEAN NOT NULL DEFAULT FALSE,
    KEY ix_watchlist_market_ready (delivered, next_attempt_at, instrument_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
