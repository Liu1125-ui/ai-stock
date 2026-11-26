ALTER TABLE training_dispatch_inbox
    ADD COLUMN execution_state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'QUEUED',
    ADD COLUMN execution_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN started_at DATETIME(3) NULL,
    ADD COLUMN finished_at DATETIME(3) NULL,
    ADD CONSTRAINT chk_training_execution_state CHECK (execution_state IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED')),
    ADD CONSTRAINT chk_training_execution_token CHECK (
        (execution_state='RUNNING' AND execution_token IS NOT NULL) OR
        (execution_state<>'RUNNING' AND execution_token IS NULL)
    ),
    ADD INDEX ix_training_execution_queue (execution_state,received_at,task_id);

CREATE TABLE training_execution_slot (
    slot_id TINYINT NOT NULL,
    task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    PRIMARY KEY (slot_id),
    CONSTRAINT chk_training_single_slot CHECK (slot_id=1),
    CONSTRAINT fk_training_execution_slot FOREIGN KEY (task_id) REFERENCES training_dispatch_inbox(task_id)
) ENGINE=InnoDB;
INSERT INTO training_execution_slot(slot_id,task_id) VALUES (1,NULL);
