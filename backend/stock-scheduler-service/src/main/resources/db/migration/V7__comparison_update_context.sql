ALTER TABLE scheduled_task DROP CHECK ck_task_type, DROP CHECK ck_task_payload,
 ADD CONSTRAINT ck_task_type CHECK(task_type IN ('MARKET_REFRESH','PREDICTION_VERIFY','COMPARISON_UPDATE')),
 ADD CONSTRAINT ck_task_payload CHECK(
  (task_type IN ('MARKET_REFRESH','COMPARISON_UPDATE') AND prediction_id IS NULL AND rule_version IS NULL)
  OR (task_type='PREDICTION_VERIFY' AND prediction_id IS NOT NULL AND rule_version IS NOT NULL));
CREATE TABLE comparison_update_context (
 task_id CHAR(36) PRIMARY KEY, cycle_date DATE NOT NULL,
 instrument_id VARCHAR(64) NOT NULL, raw_version_id CHAR(36) NOT NULL,
 raw_content_hash CHAR(64) NOT NULL, range_start DATE NOT NULL, target_date DATE NOT NULL,
 definition VARCHAR(64) NOT NULL, plan_as_of DATETIME(3) NOT NULL,
 attempt_limit TINYINT NOT NULL,
 UNIQUE KEY uk_comparison_cycle(cycle_date,instrument_id,definition),
 FOREIGN KEY(task_id) REFERENCES scheduled_task(task_id),
 CHECK(attempt_limit BETWEEN 1 AND 3),CHECK(range_start<=target_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
