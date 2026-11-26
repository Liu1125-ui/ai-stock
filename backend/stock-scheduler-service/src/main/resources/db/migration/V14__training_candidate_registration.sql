ALTER TABLE training_dispatch_inbox
    ADD COLUMN registered_model_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN registration_due_at DATETIME(3) NOT NULL DEFAULT '1970-01-01 00:00:00.000',
    ADD INDEX ix_training_registration_due (execution_state,registered_model_id,registration_due_at,task_id);
