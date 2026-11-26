CREATE TABLE index_market_snapshot (
    snapshot_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    range_start DATE NOT NULL,
    range_end DATE NOT NULL,
    trade_date DATE NOT NULL,
    fetched_at DATETIME(6) NOT NULL,
    source_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    candidate_json JSON NOT NULL,
    INDEX ix_index_snapshot_latest (trade_date, fetched_at, snapshot_id),
    CHECK (range_start <= trade_date AND trade_date <= range_end)
) ENGINE=InnoDB;
