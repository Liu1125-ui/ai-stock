CREATE TABLE market_refresh_receipt (
 operation_id VARCHAR(128) PRIMARY KEY, request_hash CHAR(64) NOT NULL,
 instrument_id VARCHAR(64) NOT NULL, target_date DATE NOT NULL, lease_until DATETIME(3) NOT NULL,
 state VARCHAR(24) NOT NULL, version_id CHAR(36) NULL, error_code VARCHAR(64) NULL,
 created_at DATETIME(3) NOT NULL, finished_at DATETIME(3) NULL,
 CHECK(state IN ('RUNNING','AVAILABLE','LEGITIMATE_EMPTY','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
