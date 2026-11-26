ALTER TABLE training_submission_item
    ADD COLUMN dispatch_state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING',
    ADD COLUMN dispatch_started BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN dispatch_claim CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN dispatch_due_at DATETIME(3) NOT NULL DEFAULT '1970-01-01 00:00:00.000',
    ADD CONSTRAINT chk_training_dispatch_state CHECK (dispatch_state IN ('PENDING','CONFIRMED','CONFLICT')),
    ADD INDEX ix_training_dispatch_due (dispatch_state,dispatch_due_at,task_id);
