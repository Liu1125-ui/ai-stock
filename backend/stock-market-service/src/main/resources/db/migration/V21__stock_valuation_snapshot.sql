CREATE TABLE stock_valuation_snapshot (
    instrument_id VARCHAR(64) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    trade_date DATE NOT NULL,
    evidence_version_id CHAR(36) NOT NULL,
    pe_ttm DECIMAL(20,8) NULL,
    pb_mrq DECIMAL(20,8) NULL,
    fetched_at DATETIME(6) NOT NULL,
    PRIMARY KEY (instrument_id, provider, trade_date),
    FOREIGN KEY (evidence_version_id) REFERENCES market_data_version(version_id),
    CHECK (pe_ttm IS NOT NULL OR pb_mrq IS NOT NULL),
    CHECK (pe_ttm IS NULL OR pe_ttm <> 0),
    CHECK (pb_mrq IS NULL OR pb_mrq <> 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
