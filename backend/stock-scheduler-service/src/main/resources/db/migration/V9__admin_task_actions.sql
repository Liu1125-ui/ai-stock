ALTER TABLE scheduled_task
 ADD COLUMN revision BIGINT NOT NULL DEFAULT 0,
 ADD CONSTRAINT ck_task_revision CHECK (revision >= 0);

CREATE TABLE admin_task_operation (
 operation_id CHAR(36) PRIMARY KEY,
 task_id CHAR(36) NOT NULL,
 actor_user_id CHAR(36) NOT NULL,
 action VARCHAR(16) NOT NULL,
 request_hash CHAR(64) NOT NULL,
 expected_revision BIGINT NOT NULL,
 reason_code VARCHAR(32) NOT NULL,
 outcome VARCHAR(16) NOT NULL,
 result_code VARCHAR(64) NULL,
 before_state VARCHAR(24) NULL,
 after_state VARCHAR(24) NULL,
 before_revision BIGINT NULL,
 after_revision BIGINT NULL,
 requested_at DATETIME(3) NOT NULL,
 finished_at DATETIME(3) NULL,
 KEY ix_admin_task_history(task_id,requested_at,operation_id),
 KEY ix_admin_task_retry(task_id,action,outcome,finished_at,operation_id),
 CHECK (action IN ('RETRY','CANCEL')),
 CHECK (outcome IN ('PENDING','APPLIED','REJECTED')),
 CHECK (expected_revision >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
