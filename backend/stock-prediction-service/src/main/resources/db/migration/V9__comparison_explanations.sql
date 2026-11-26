CREATE TABLE comparison_ai_result (
 explanation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 sequence_id BIGINT NOT NULL AUTO_INCREMENT UNIQUE,
 user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 comparison_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 context_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 context_json JSON NOT NULL, paragraphs_json JSON NOT NULL,
 created_at DATETIME(3) NOT NULL,
 UNIQUE KEY uq_comparison_ai_context(user_id,comparison_id,context_hash),
 KEY ix_comparison_ai_result_owner(user_id,comparison_id,sequence_id),
 FOREIGN KEY(comparison_id) REFERENCES stock_comparison_result(comparison_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE comparison_ai_operation (
 operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 sequence_id BIGINT NOT NULL AUTO_INCREMENT UNIQUE,
 user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 comparison_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 context_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 context_json JSON NOT NULL,
 state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 explanation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 error_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
 created_at DATETIME(3) NOT NULL,lease_until DATETIME(3) NOT NULL,
 finished_at DATETIME(3) NULL,
 KEY ix_comparison_ai_op_owner(user_id,comparison_id,sequence_id),
 FOREIGN KEY(comparison_id) REFERENCES stock_comparison_result(comparison_id),
 FOREIGN KEY(explanation_id) REFERENCES comparison_ai_result(explanation_id),
 CHECK((state='RUNNING' AND explanation_id IS NULL AND error_code IS NULL AND finished_at IS NULL)
 OR (state='SUCCEEDED' AND explanation_id IS NOT NULL AND error_code IS NULL AND finished_at IS NOT NULL)
 OR (state='FAILED' AND explanation_id IS NULL AND error_code IS NOT NULL AND finished_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
