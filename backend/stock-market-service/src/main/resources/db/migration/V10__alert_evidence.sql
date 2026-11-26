CREATE TABLE alert_evidence_version (
 evidence_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 instrument_id VARCHAR(16) NOT NULL,raw_version_id CHAR(36) NOT NULL,trade_date DATE NOT NULL,
 content_hash CHAR(64) NOT NULL,evidence_json LONGTEXT NOT NULL,created_at TIMESTAMP(6) NOT NULL,
 CHECK(JSON_VALID(evidence_json)),KEY ix_alert_evidence_stock(instrument_id,trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE alert_evidence_operation (
 operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,request_hash CHAR(64) NOT NULL,
 state VARCHAR(20) NOT NULL,evidence_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,error_code VARCHAR(64) NULL,
 lease_until TIMESTAMP(6) NOT NULL,created_at TIMESTAMP(6) NOT NULL,finished_at TIMESTAMP(6) NULL,
 CHECK(state IN('RUNNING','AVAILABLE','FAILED','SKIPPED_STALE')),
 CHECK((state='AVAILABLE' AND evidence_id IS NOT NULL) OR (state<>'AVAILABLE' AND evidence_id IS NULL))
) ENGINE=InnoDB;
