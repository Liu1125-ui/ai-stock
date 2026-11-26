CREATE TABLE training_submission (
    owner_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    configuration_ids JSON NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (owner_id, request_id)
) ENGINE=InnoDB;

CREATE TABLE training_submission_item (
    task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    configuration_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    configuration_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    dataset_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (task_id),
    UNIQUE KEY uq_training_submission_config (owner_id, request_id, configuration_id),
    CONSTRAINT fk_training_submission_item FOREIGN KEY (owner_id, request_id)
        REFERENCES training_submission(owner_id, request_id)
) ENGINE=InnoDB;
