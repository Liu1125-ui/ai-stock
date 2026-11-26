CREATE TABLE stock_comparison_result (
 comparison_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 sequence_id BIGINT NOT NULL AUTO_INCREMENT UNIQUE,
 owner_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 result_json JSON NOT NULL, summary_json JSON NOT NULL, source_snapshots_json JSON NOT NULL,
 source_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_at DATETIME(3) NOT NULL,
 KEY ix_comparison_owner(owner_id,sequence_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE stock_comparison_operation (
 owner_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 input_json JSON NOT NULL, state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 lease_until DATETIME(3) NULL, lease_generation CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 result_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 error_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL, created_at DATETIME(3) NOT NULL,
 PRIMARY KEY(owner_id,request_id), FOREIGN KEY(result_id) REFERENCES stock_comparison_result(comparison_id),
 CHECK(state IN ('RUNNING','SUCCEEDED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
