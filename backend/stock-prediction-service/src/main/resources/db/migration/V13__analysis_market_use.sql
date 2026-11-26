CREATE TABLE stock_analysis_market_use (
 operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 execution_generation BIGINT NOT NULL,
 request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 raw_version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 registration_started BOOLEAN NOT NULL DEFAULT FALSE,
 bundle_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL UNIQUE,
 receipt_json JSON NULL,
 completion_acked BOOLEAN NOT NULL DEFAULT FALSE,
 created_at DATETIME(3) NOT NULL,
 last_checked_at DATETIME(3) NOT NULL,
 FOREIGN KEY (operation_id) REFERENCES stock_analysis_operation(operation_id),
 CHECK (execution_generation > 0),
 CHECK ((bundle_id IS NULL AND receipt_json IS NULL) OR (bundle_id IS NOT NULL AND receipt_json IS NOT NULL)),
 CHECK (completion_acked = FALSE OR bundle_id IS NOT NULL),
 KEY ix_analysis_use_recovery(completion_acked,registration_started,last_checked_at,operation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
