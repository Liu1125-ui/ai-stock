CREATE TABLE comparison_evidence_version (
 evidence_id CHAR(36) PRIMARY KEY, instrument_id VARCHAR(64) NOT NULL,
 raw_version_id CHAR(36) NOT NULL, content_hash CHAR(64) NOT NULL,
 evidence_json JSON NOT NULL, created_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_comparison_content(content_hash),
 FOREIGN KEY(raw_version_id) REFERENCES market_data_version(version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE comparison_evidence_operation (
 operation_id VARCHAR(128) PRIMARY KEY, request_hash CHAR(64) NOT NULL,
 evidence_id CHAR(36) NULL, created_at DATETIME(3) NOT NULL,
 FOREIGN KEY(evidence_id) REFERENCES comparison_evidence_version(evidence_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;