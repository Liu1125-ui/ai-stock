CREATE TABLE admin_market_refresh_context (
 task_id CHAR(36) PRIMARY KEY, instrument_id VARCHAR(64) NOT NULL,
 base_version_id CHAR(36) NOT NULL, head_revision BIGINT NOT NULL,
 coverage_start DATE NOT NULL,target_date DATE NOT NULL,policy_version VARCHAR(64) NOT NULL,
 created_at DATETIME(3) NOT NULL, FOREIGN KEY(task_id) REFERENCES scheduled_task(task_id),
 CHECK(head_revision>=0),CHECK(coverage_start<=target_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE admin_market_refresh_guard (
 instrument_id VARCHAR(64) PRIMARY KEY, active_task_id CHAR(36) NULL,last_admitted_at DATETIME(3) NULL,
 FOREIGN KEY(active_task_id) REFERENCES scheduled_task(task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE admin_market_refresh_operation (
 operation_id CHAR(36) PRIMARY KEY,actor_user_id CHAR(36) NOT NULL,instrument_id VARCHAR(64) NOT NULL,
 request_hash CHAR(64) NOT NULL,reason_code VARCHAR(32) NOT NULL,base_version_id CHAR(36) NOT NULL,
 head_revision BIGINT NOT NULL,coverage_start DATE NULL,target_date DATE NOT NULL,policy_version VARCHAR(64) NOT NULL,
 outcome VARCHAR(16) NOT NULL,result_code VARCHAR(64) NULL,task_id CHAR(36) NULL,
 requested_at DATETIME(3) NOT NULL,finished_at DATETIME(3) NULL,
 KEY ix_refresh_history(instrument_id,requested_at,operation_id),
 FOREIGN KEY(task_id) REFERENCES scheduled_task(task_id),CHECK(head_revision>=0),
 CHECK(outcome IN ('PENDING','QUEUED','ATTACHED','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;