CREATE TABLE training_admin_cancellation (
    task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reason_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    cancelled_at DATETIME(3) NOT NULL,
    PRIMARY KEY (task_id),
    UNIQUE KEY uq_training_cancel_operation (operation_id),
    CONSTRAINT fk_training_cancel_task FOREIGN KEY (task_id) REFERENCES training_dispatch_inbox(task_id)
) ENGINE=InnoDB;

CREATE INDEX ix_training_admin_received ON training_dispatch_inbox(received_at,task_id);
