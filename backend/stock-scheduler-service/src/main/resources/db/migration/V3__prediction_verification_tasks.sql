ALTER TABLE scheduled_task ADD COLUMN prediction_id CHAR(36) NULL,
 ADD COLUMN rule_version VARCHAR(64) NULL,
 ADD CONSTRAINT ck_task_type CHECK(task_type IN ('MARKET_REFRESH','PREDICTION_VERIFY')),
 ADD CONSTRAINT ck_task_payload CHECK((task_type='MARKET_REFRESH' AND prediction_id IS NULL AND rule_version IS NULL) OR (task_type='PREDICTION_VERIFY' AND prediction_id IS NOT NULL AND rule_version IS NOT NULL)),
 ADD KEY ix_verification_prediction(prediction_id,target_date);
ALTER TABLE scheduled_task DROP CHECK ck_task_state,
 ADD CONSTRAINT ck_task_state CHECK(state IN ('QUEUED','RUNNING','RECONCILING','RETRY_WAIT','SUCCEEDED','EMPTY','SKIPPED','FAILED','CANCELLED'));
ALTER TABLE task_attempt DROP CHECK ck_attempt_state,
 ADD CONSTRAINT ck_attempt_state CHECK(state IN ('RUNNING','UNKNOWN','SUCCEEDED','EMPTY','SKIPPED','FAILED','EXPIRED'));
CREATE TABLE verification_scan (
 plan_id INT PRIMARY KEY, cutoff_date DATE NOT NULL, after_id CHAR(36) NULL,
 revision BIGINT NOT NULL DEFAULT 0, updated_at DATETIME(3) NOT NULL,
 CONSTRAINT ck_verification_scan_singleton CHECK(plan_id=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
