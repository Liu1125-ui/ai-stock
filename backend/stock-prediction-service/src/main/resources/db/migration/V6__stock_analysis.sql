ALTER TABLE prediction_explanation_slot ADD COLUMN operation_kind VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL;
UPDATE prediction_explanation_slot SET operation_kind='PREDICTION_EXPLANATION' WHERE operation_id IS NOT NULL;
CREATE TABLE stock_analysis_result(
 analysis_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 sequence_id BIGINT NOT NULL AUTO_INCREMENT UNIQUE,
 user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 instrument_id VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 range_from DATE NOT NULL,range_to DATE NOT NULL,
 market_version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 context_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 context_json JSON NOT NULL,paragraphs_json JSON NOT NULL,
 created_at DATETIME(3) NOT NULL,
 UNIQUE KEY ux_analysis_context(user_id,context_hash),
 KEY ix_analysis_list(user_id,instrument_id,range_from,range_to,sequence_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE stock_analysis_operation(
 operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 sequence_id BIGINT NOT NULL AUTO_INCREMENT UNIQUE,
 user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 instrument_id VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 range_from DATE NOT NULL,range_to DATE NOT NULL,
 market_version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 context_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 context_json JSON NOT NULL,state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 analysis_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 error_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
 created_at DATETIME(3) NOT NULL,lease_until DATETIME(3) NOT NULL,finished_at DATETIME(3) NULL,
 CHECK(state IN ('RUNNING','SUCCEEDED','FAILED')),
 FOREIGN KEY(analysis_id) REFERENCES stock_analysis_result(analysis_id),
 KEY ix_analysis_attempt(user_id,instrument_id,range_from,range_to,sequence_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
