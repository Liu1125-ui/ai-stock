ALTER TABLE weekly_training_plan
    ADD COLUMN admitted_owner CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN admitted_task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN admitted_at DATETIME(3) NULL,
    ADD UNIQUE KEY uq_weekly_training_admitted_task (admitted_task_id),
    ADD CONSTRAINT ck_weekly_training_admission CHECK (
        (admitted_owner IS NULL AND admitted_task_id IS NULL AND admitted_at IS NULL)
        OR
        (state='PLANNED' AND admitted_owner IS NOT NULL AND admitted_task_id IS NOT NULL AND admitted_at IS NOT NULL)
    );
