ALTER TABLE instrument ADD COLUMN listing_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
 ADD COLUMN name_verified BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN metadata_source VARCHAR(20) NULL,
 ADD COLUMN metadata_checked_at DATETIME(3) NULL;
CREATE TABLE source_code_map (
 provider VARCHAR(20) NOT NULL, source_code VARCHAR(32) NOT NULL, instrument_id VARCHAR(64) NOT NULL,
 PRIMARY KEY(provider,source_code), UNIQUE KEY uk_source_instrument(provider,instrument_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE market_data_version (
 version_id CHAR(36) PRIMARY KEY, instrument_id VARCHAR(64) NOT NULL, provider VARCHAR(20) NOT NULL,
 price_basis VARCHAR(24) NOT NULL, status VARCHAR(16) NOT NULL, quality VARCHAR(32) NOT NULL,
 content_hash CHAR(64) NOT NULL, source_payload_hash CHAR(64) NOT NULL, source_version_id VARCHAR(128) NULL,
 fetched_at DATETIME(3) NOT NULL, published_at DATETIME(3) NOT NULL,
 coverage_start DATE NOT NULL, coverage_end DATE NOT NULL, calendar_start DATE NULL, calendar_end DATE NULL,
 UNIQUE KEY uk_version_content(instrument_id,provider,price_basis,content_hash),
 CHECK(price_basis='RAW'), CHECK(coverage_end>=coverage_start),
 CHECK(status IN ('STAGING','AVAILABLE','REJECTED','UNAVAILABLE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE market_daily_bar (
 version_id CHAR(36) NOT NULL, trade_date DATE NOT NULL,
 open_price DECIMAL(20,6) NOT NULL, high_price DECIMAL(20,6) NOT NULL,
 low_price DECIMAL(20,6) NOT NULL, close_price DECIMAL(20,6) NOT NULL,
 volume_shares DECIMAL(20,4) NULL, amount_yuan DECIMAL(24,6) NULL,
 trade_status SMALLINT NOT NULL, source_adjustment_ratio DECIMAL(24,12) NULL,
 PRIMARY KEY(version_id,trade_date), FOREIGN KEY(version_id) REFERENCES market_data_version(version_id),
 CHECK(open_price>0 AND high_price>0 AND low_price>0 AND close_price>0),
 CHECK(high_price>=open_price AND high_price>=close_price AND high_price>=low_price),
 CHECK(low_price<=open_price AND low_price<=close_price), CHECK(trade_status IN (0,1)),
 CHECK(volume_shares IS NULL OR volume_shares>=0), CHECK(amount_yuan IS NULL OR amount_yuan>=0),
 CHECK(trade_status=0 OR (volume_shares IS NOT NULL AND amount_yuan IS NOT NULL AND volume_shares>0 AND amount_yuan>0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE market_calendar_day (
 version_id CHAR(36) NOT NULL, calendar_date DATE NOT NULL, is_open BOOLEAN NOT NULL,
 PRIMARY KEY(version_id,calendar_date), FOREIGN KEY(version_id) REFERENCES market_data_version(version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE instrument_data_head (
 instrument_id VARCHAR(64) NOT NULL, provider VARCHAR(20) NOT NULL, price_basis VARCHAR(24) NOT NULL,
 version_id CHAR(36) NULL, revision BIGINT NOT NULL DEFAULT 0,
 checked_at DATETIME(3) NULL, last_error_code VARCHAR(64) NULL,
 PRIMARY KEY(instrument_id,provider,price_basis), FOREIGN KEY(version_id) REFERENCES market_data_version(version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE market_ingestion_operation (
 operation_id VARCHAR(128) PRIMARY KEY, request_hash CHAR(64) NOT NULL,
 instrument_id VARCHAR(64) NULL, provider VARCHAR(20) NULL, state VARCHAR(16) NOT NULL,
 version_id CHAR(36) NULL, head_revision BIGINT NULL, error_code VARCHAR(64) NULL,
 created_at DATETIME(3) NOT NULL, finished_at DATETIME(3) NULL,
 CHECK(state IN ('PENDING','AVAILABLE','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
