CREATE TABLE market_update_plan (
 instrument_id VARCHAR(64) PRIMARY KEY, enabled BOOLEAN NOT NULL, last_target DATE NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE scheduled_task (
 task_id CHAR(36) PRIMARY KEY, task_type VARCHAR(32) NOT NULL,
 instrument_id VARCHAR(64) NOT NULL, target_date DATE NOT NULL, idempotency_key VARCHAR(128) NOT NULL UNIQUE,
 state VARCHAR(24) NOT NULL, attempt_count INT NOT NULL, current_attempt CHAR(36) NULL, lease_until DATETIME(3) NULL,
 next_at DATETIME(3) NOT NULL, version_id CHAR(36) NULL, error_code VARCHAR(64) NULL,
 created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 KEY ix_task_claim(state,next_at), KEY ix_task_lease(state,lease_until),
 CHECK(state IN ('QUEUED','RUNNING','RETRY_WAIT','SUCCEEDED','EMPTY','FAILED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE task_attempt (
 attempt_id CHAR(36) PRIMARY KEY, task_id CHAR(36) NOT NULL, ordinal INT NOT NULL,
 state VARCHAR(24) NOT NULL, started_at DATETIME(3) NOT NULL, lease_until DATETIME(3) NOT NULL,
 finished_at DATETIME(3) NULL, version_id CHAR(36) NULL, error_code VARCHAR(64) NULL,
 UNIQUE KEY uk_attempt_ordinal(task_id,ordinal), FOREIGN KEY(task_id) REFERENCES scheduled_task(task_id),
 CHECK(state IN ('RUNNING','SUCCEEDED','EMPTY','FAILED','EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
