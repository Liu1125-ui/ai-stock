CREATE TABLE weekly_training_plan (
    scope_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    week_start DATE NOT NULL,
    state VARCHAR(12) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reason VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    checked_at DATETIME(3) NOT NULL,
    snapshot_session DATE NULL,
    dataset_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    configuration_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    configuration_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    model_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    model_revision BIGINT NULL,
    head_revision BIGINT NULL,
    PRIMARY KEY (scope_key,week_start),
    UNIQUE KEY uq_weekly_training_request (request_id),
    INDEX ix_weekly_training_snapshot (scope_key,state,snapshot_session,dataset_sha256),
    CONSTRAINT ck_weekly_training_state CHECK (
        (state='WAITING' AND request_id IS NULL AND configuration_id IS NULL AND configuration_sha256 IS NULL
            AND model_id IS NULL AND model_revision IS NULL AND head_revision IS NULL)
        OR
        (state='PLANNED' AND reason='READY' AND request_id IS NOT NULL AND snapshot_session IS NOT NULL
            AND dataset_sha256 IS NOT NULL AND configuration_id IS NOT NULL AND configuration_sha256 IS NOT NULL
            AND model_id IS NOT NULL AND model_revision IS NOT NULL AND head_revision IS NOT NULL)
    )
) ENGINE=InnoDB;
