CREATE TABLE portfolio_question_result (
 answer_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 history_seq BIGINT NOT NULL AUTO_INCREMENT UNIQUE,
 owner_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 analysis_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 question VARCHAR(1200) NOT NULL, context_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 context_json JSON NOT NULL, status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 topic VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, paragraphs_json JSON NOT NULL,
 created_at DATETIME(3) NOT NULL,
 UNIQUE KEY uq_portfolio_question_owner_answer(owner_id,answer_id),
 UNIQUE KEY uq_portfolio_question_context(owner_id,analysis_id,context_hash),
 KEY ix_portfolio_question_history(owner_id,analysis_id,history_seq),
 FOREIGN KEY(owner_id,analysis_id) REFERENCES portfolio_analysis_result(owner_id,analysis_id),
 CHECK(status IN('ANSWERED','OUT_OF_SCOPE')),
 CHECK(topic IN('PROFIT','RETURN','CONCENTRATION','COVERAGE','LIMITATION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE portfolio_question_operation (
 operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 history_seq BIGINT NOT NULL AUTO_INCREMENT UNIQUE,
 owner_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 analysis_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 question VARCHAR(1200) NOT NULL,
 request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 context_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 context_json JSON NOT NULL, state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 answer_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 error_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
 accepted_at DATETIME(3) NOT NULL, deadline_at DATETIME(3) NOT NULL,
 lease_until DATETIME(3) NOT NULL, generation CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 finished_at DATETIME(3) NULL,
 KEY ix_portfolio_question_operation(owner_id,analysis_id,history_seq),
 FOREIGN KEY(owner_id,analysis_id) REFERENCES portfolio_analysis_result(owner_id,analysis_id),
 FOREIGN KEY(owner_id,answer_id) REFERENCES portfolio_question_result(owner_id,answer_id),
 CHECK(deadline_at>accepted_at AND lease_until>=deadline_at),
 CHECK((state='RUNNING' AND answer_id IS NULL AND error_code IS NULL AND finished_at IS NULL)
 OR (state='SUCCEEDED' AND answer_id IS NOT NULL AND error_code IS NULL AND finished_at IS NOT NULL)
 OR (state='FAILED' AND answer_id IS NULL AND error_code IS NOT NULL AND finished_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
