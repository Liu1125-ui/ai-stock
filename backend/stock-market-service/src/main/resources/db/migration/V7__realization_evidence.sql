CREATE TABLE realization_operation (
 operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 state VARCHAR(24) NOT NULL,
 version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 error_code VARCHAR(80) NULL,
 lease_until TIMESTAMP(6) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL,
 finished_at TIMESTAMP(6) NULL,
 CONSTRAINT ck_realization_operation CHECK(state IN ('RUNNING','AVAILABLE','FAILED','SKIPPED_NOT_DUE'))
) ENGINE=InnoDB;
CREATE TABLE realization_version (
 version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 instrument_id VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 raw_version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 evidence_json LONGTEXT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT ck_realization_json CHECK(JSON_VALID(evidence_json))
) ENGINE=InnoDB;
