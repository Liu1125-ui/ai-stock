CREATE TABLE stock_question_result (
 answer_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 sequence_id BIGINT NOT NULL AUTO_INCREMENT UNIQUE,
 user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 analysis_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 question VARCHAR(1200) NOT NULL, question_hash CHAR(64) CHARACTER SET ascii NOT NULL,
 context_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,context_json JSON NOT NULL,
 status VARCHAR(16) CHARACTER SET ascii NOT NULL,topic VARCHAR(20) CHARACTER SET ascii NOT NULL,
 paragraphs_json JSON NOT NULL,created_at DATETIME(3) NOT NULL,
 UNIQUE KEY ux_question_context(user_id,context_hash),KEY ix_question_list(user_id,analysis_id,sequence_id),
 FOREIGN KEY(analysis_id) REFERENCES stock_analysis_result(analysis_id),
 CHECK(status IN ('ANSWERED','OUT_OF_SCOPE')),CHECK(topic IN ('INDICATOR','PREDICTION','VERIFICATION','RISK','LIMITATION')),
 CHECK((status='ANSWERED' AND JSON_LENGTH(paragraphs_json)=3) OR (status='OUT_OF_SCOPE' AND topic='LIMITATION' AND JSON_LENGTH(paragraphs_json)=0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE stock_question_operation (
 operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,sequence_id BIGINT NOT NULL AUTO_INCREMENT UNIQUE,
 user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,analysis_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 question VARCHAR(1200) NOT NULL,request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 context_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,context_json JSON NOT NULL,
 state VARCHAR(16) CHARACTER SET ascii NOT NULL,answer_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,error_code VARCHAR(64) CHARACTER SET ascii NULL,
 created_at DATETIME(3) NOT NULL,lease_until DATETIME(3) NOT NULL,finished_at DATETIME(3) NULL,
 FOREIGN KEY(analysis_id) REFERENCES stock_analysis_result(analysis_id),FOREIGN KEY(answer_id) REFERENCES stock_question_result(answer_id),KEY ix_question_attempt(user_id,analysis_id,sequence_id),
 CHECK((state='RUNNING' AND answer_id IS NULL AND error_code IS NULL AND finished_at IS NULL) OR (state='SUCCEEDED' AND answer_id IS NOT NULL AND error_code IS NULL AND finished_at IS NOT NULL) OR (state='FAILED' AND answer_id IS NULL AND error_code IS NOT NULL AND finished_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
