CREATE TABLE forecast_verification_head (
 prediction_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 current_version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 due_through DATE NULL,
 last_sequence BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB;
CREATE TABLE forecast_verification_operation (
 operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 sequence_id BIGINT NOT NULL AUTO_INCREMENT UNIQUE,
 prediction_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 state VARCHAR(24) NOT NULL,
 version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 error_code VARCHAR(80) NULL,
 due_through DATE NOT NULL,
 lease_until TIMESTAMP(6) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL,
 finished_at TIMESTAMP(6) NULL,
 CONSTRAINT ck_verification_operation CHECK(state IN ('RUNNING','AVAILABLE','FAILED','SKIPPED_NOT_DUE'))
) ENGINE=InnoDB;
CREATE TABLE forecast_verification_version (
 version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 prediction_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 realization_version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 rule_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 due_through DATE NOT NULL,
 content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 evidence_json LONGTEXT NOT NULL,
 evaluation_json LONGTEXT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL,
 UNIQUE KEY uq_verification_evidence(prediction_id,realization_version_id,rule_version),
 CONSTRAINT ck_verification_evidence_json CHECK(JSON_VALID(evidence_json)),
 CONSTRAINT ck_verification_result_json CHECK(JSON_VALID(evaluation_json))
) ENGINE=InnoDB;
