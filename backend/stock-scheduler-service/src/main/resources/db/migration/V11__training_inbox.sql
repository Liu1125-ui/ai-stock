CREATE TABLE training_dispatch_inbox (
    task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    configuration_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    configuration_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    dataset_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    received_at DATETIME(3) NOT NULL,
    PRIMARY KEY (task_id)
) ENGINE=InnoDB;
