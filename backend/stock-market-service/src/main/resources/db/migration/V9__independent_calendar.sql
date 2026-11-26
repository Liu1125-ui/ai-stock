CREATE TABLE independent_calendar_version (
 version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 provider VARCHAR(16) NOT NULL,range_start DATE NOT NULL,range_end DATE NOT NULL,
 fetched_at TIMESTAMP(6) NOT NULL,source_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 candidate_json LONGTEXT NOT NULL,CHECK(JSON_VALID(candidate_json)),CHECK(range_end>=range_start)
) ENGINE=InnoDB;
CREATE TABLE independent_calendar_head (
 provider VARCHAR(16) PRIMARY KEY,version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL
) ENGINE=InnoDB;
CREATE TABLE independent_calendar_operation (
 operation_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 range_start DATE NOT NULL,range_end DATE NOT NULL,
 version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
) ENGINE=InnoDB;
