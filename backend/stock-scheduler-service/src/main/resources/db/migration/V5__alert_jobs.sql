CREATE TABLE alert_evidence_work (
 work_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,instrument_id VARCHAR(16) NOT NULL,raw_version_id CHAR(36) NOT NULL,trade_date DATE NOT NULL,round_id VARCHAR(64) NOT NULL,
 state VARCHAR(16) NOT NULL,operation_id CHAR(36) NULL,lease_until TIMESTAMP(6) NULL,attempt_count INT NOT NULL,evidence_id CHAR(36) NULL,cache_until TIMESTAMP(6) NULL,error_code VARCHAR(64) NULL,next_at TIMESTAMP(6) NOT NULL,updated_at TIMESTAMP(6) NOT NULL,
 CHECK(state IN('READY','RUNNING','AVAILABLE','RETRY_WAIT','FAILED')),CHECK(attempt_count BETWEEN 0 AND 3),CHECK(state<>'AVAILABLE' OR (evidence_id IS NOT NULL AND cache_until IS NOT NULL)),CHECK(state<>'RUNNING' OR (operation_id IS NOT NULL AND lease_until IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE alert_job (
 job_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,job_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,active_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL UNIQUE,
 rule_id CHAR(36) NOT NULL,rule_revision BIGINT NOT NULL,instrument_id VARCHAR(16) NOT NULL,trade_date DATE NOT NULL,raw_version_id CHAR(36) NOT NULL,round_id VARCHAR(64) NOT NULL,work_key CHAR(64) NOT NULL,
 evidence_id CHAR(36) NULL,stage VARCHAR(24) NOT NULL,state VARCHAR(16) NOT NULL,attempt_count INT NOT NULL,current_attempt CHAR(36) NULL,lease_until TIMESTAMP(6) NULL,error_code VARCHAR(64) NULL,
 next_at TIMESTAMP(6) NOT NULL,created_at TIMESTAMP(6) NOT NULL,updated_at TIMESTAMP(6) NOT NULL,
 KEY ix_alert_job_due(state,next_at,created_at,job_id),KEY ix_alert_job_work(work_key,state),CHECK(rule_revision>=0),CHECK(attempt_count BETWEEN 0 AND 3),
 CHECK(stage IN('PREPARE_EVIDENCE','EVALUATE_RULE')),CHECK(state IN('QUEUED','RUNNING','RECONCILING','RETRY_WAIT','SUCCEEDED','SKIPPED','FAILED')),
 CHECK((state IN('QUEUED','RUNNING','RECONCILING','RETRY_WAIT') AND active_key IS NOT NULL) OR (state IN('SUCCEEDED','SKIPPED','FAILED') AND active_key IS NULL)),
 CHECK((state IN('RUNNING','RECONCILING') AND current_attempt IS NOT NULL AND lease_until IS NOT NULL) OR (state NOT IN('RUNNING','RECONCILING') AND current_attempt IS NULL AND lease_until IS NULL)),CHECK(stage<>'EVALUATE_RULE' OR evidence_id IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE alert_job_attempt (
 attempt_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,job_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,ordinal INT NOT NULL,stage VARCHAR(24) NOT NULL,state VARCHAR(16) NOT NULL,
 lease_until TIMESTAMP(6) NOT NULL,started_at TIMESTAMP(6) NOT NULL,finished_at TIMESTAMP(6) NULL,error_code VARCHAR(64) NULL,reference_id CHAR(36) NULL,
 UNIQUE KEY uq_alert_attempt(job_id,ordinal),CONSTRAINT fk_alert_attempt_job FOREIGN KEY(job_id) REFERENCES alert_job(job_id),CHECK(ordinal>0),CHECK(stage IN('PREPARE_EVIDENCE','EVALUATE_RULE')),CHECK(state IN('RUNNING','UNKNOWN','SUCCEEDED','SKIPPED','FAILED')),
 CHECK((state IN('RUNNING','UNKNOWN') AND finished_at IS NULL) OR (state IN('SUCCEEDED','SKIPPED','FAILED') AND finished_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
