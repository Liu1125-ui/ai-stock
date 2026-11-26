ALTER TABLE alert_evaluation_operation ADD COLUMN fencing_revision BIGINT NOT NULL DEFAULT 1,
 ADD CONSTRAINT ck_alert_use_fence CHECK (fencing_revision > 0);
CREATE TABLE alert_evaluation_market_use (
 operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 owner_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 evidence_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 instrument_id VARCHAR(16) NOT NULL,
 raw_version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 market_request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 registration_started BOOLEAN NOT NULL DEFAULT FALSE,
 bundle_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL UNIQUE,
 receipt_json JSON NULL,
 completion_acked BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMP(6) NOT NULL,
 last_checked_at TIMESTAMP(6) NOT NULL,
 FOREIGN KEY (operation_id) REFERENCES alert_evaluation_operation(operation_id),
 CHECK ((bundle_id IS NULL AND receipt_json IS NULL) OR (bundle_id IS NOT NULL AND receipt_json IS NOT NULL)),
 CHECK (completion_acked = FALSE OR bundle_id IS NOT NULL),
 KEY ix_alert_use_pending(completion_acked,registration_started,last_checked_at,operation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
