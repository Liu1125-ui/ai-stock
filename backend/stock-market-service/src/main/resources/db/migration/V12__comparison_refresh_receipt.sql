CREATE TABLE comparison_refresh_receipt (
 operation_id CHAR(36) PRIMARY KEY, request_hash CHAR(64) NOT NULL,
 instrument_id VARCHAR(64) NOT NULL, raw_version_id CHAR(36) NOT NULL,
 raw_content_hash CHAR(64) NOT NULL, range_start DATE NOT NULL, target_date DATE NOT NULL,
 definition VARCHAR(64) NOT NULL, lease_until DATETIME(3) NOT NULL,
 state VARCHAR(16) NOT NULL, evidence_id CHAR(36) NULL, error_code VARCHAR(64) NULL,
 created_at DATETIME(3) NOT NULL, finished_at DATETIME(3) NULL,
 FOREIGN KEY(raw_version_id) REFERENCES market_data_version(version_id),
 FOREIGN KEY(evidence_id) REFERENCES comparison_evidence_version(evidence_id),
 CHECK(state IN ('RUNNING','AVAILABLE','FAILED')),
 CHECK((state='RUNNING' AND evidence_id IS NULL AND error_code IS NULL AND finished_at IS NULL)
  OR (state='AVAILABLE' AND evidence_id IS NOT NULL AND error_code IS NULL AND finished_at IS NOT NULL)
  OR (state='FAILED' AND evidence_id IS NULL AND error_code IS NOT NULL AND finished_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
