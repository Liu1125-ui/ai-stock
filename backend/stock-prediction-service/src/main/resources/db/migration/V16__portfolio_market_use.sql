ALTER TABLE portfolio_analysis_operation ADD COLUMN fencing_revision BIGINT NOT NULL DEFAULT 1,
 ADD CONSTRAINT ck_portfolio_fence CHECK (fencing_revision > 0);
CREATE TABLE portfolio_market_use (
 execution_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 owner_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 market_request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 selector_json JSON NOT NULL,
 registration_started BOOLEAN NOT NULL DEFAULT FALSE,
 bundle_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL UNIQUE,
 receipt_json JSON NULL,
 completion_acked BOOLEAN NOT NULL DEFAULT FALSE,
 created_at DATETIME(3) NOT NULL,
 last_checked_at DATETIME(3) NOT NULL,
 FOREIGN KEY (owner_id,request_id) REFERENCES portfolio_analysis_operation(owner_id,request_id),
 CHECK ((bundle_id IS NULL AND receipt_json IS NULL) OR (bundle_id IS NOT NULL AND receipt_json IS NOT NULL)),
 CHECK (completion_acked = FALSE OR bundle_id IS NOT NULL),
 KEY ix_portfolio_use_pending(completion_acked,registration_started,last_checked_at,execution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
