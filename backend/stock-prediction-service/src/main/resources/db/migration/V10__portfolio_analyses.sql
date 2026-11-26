CREATE TABLE portfolio_analysis_result (
 analysis_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 history_seq BIGINT NOT NULL AUTO_INCREMENT UNIQUE,
 owner_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 snapshot_json JSON NOT NULL, quotes_json JSON NOT NULL, result_json JSON NOT NULL,
 source_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_at DATETIME(3) NOT NULL,
 UNIQUE KEY uq_portfolio_result_owner(owner_id,analysis_id),
 KEY ix_portfolio_result_history(owner_id,history_seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE portfolio_analysis_operation (
 owner_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 accepted_at DATETIME(3) NOT NULL, deadline_at DATETIME(3) NOT NULL,
 as_of_date DATE NOT NULL, threshold_percent DECIMAL(9,6) NOT NULL,
 formula_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 lease_generation CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 lease_until DATETIME(3) NOT NULL,
 result_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 error_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
 finished_at DATETIME(3) NULL,
 PRIMARY KEY(owner_id,request_id),
 FOREIGN KEY(owner_id,result_id) REFERENCES portfolio_analysis_result(owner_id,analysis_id),
 CHECK(threshold_percent>0 AND threshold_percent<=100),
 CHECK(deadline_at>accepted_at AND lease_until>=deadline_at),
 CHECK((state='RUNNING' AND result_id IS NULL AND error_code IS NULL AND finished_at IS NULL)
 OR (state='SUCCEEDED' AND result_id IS NOT NULL AND error_code IS NULL AND finished_at IS NOT NULL)
 OR (state='FAILED' AND result_id IS NULL AND error_code IS NOT NULL AND finished_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
