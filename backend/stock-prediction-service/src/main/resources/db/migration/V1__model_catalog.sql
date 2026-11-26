CREATE TABLE model_version (
 model_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 artifact_id VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 manifest_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 checkpoint_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 status VARCHAR(16) NOT NULL,
 revision BIGINT NOT NULL DEFAULT 0,
 ever_published BOOLEAN NOT NULL DEFAULT FALSE,
 registration_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 manifest_json JSON NOT NULL,
 evaluation_json JSON NOT NULL,
 created_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT ck_model_status CHECK(status IN ('PENDING','PUBLISHED','REJECTED','OFFLINE')),
 CONSTRAINT ck_model_revision CHECK(revision>=0)
) ENGINE=InnoDB;
CREATE TABLE model_release_head (
 scope_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 current_model_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 revision BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT ck_model_head_revision CHECK(revision>=0)
) ENGINE=InnoDB;
INSERT INTO model_release_head(scope_key) VALUES('TRANSFORMER_60_5_V1');
CREATE TABLE model_release_event (
 event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL UNIQUE,
 actor_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 model_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 action_code VARCHAR(16) NOT NULL,
 reason VARCHAR(500) NOT NULL,
 request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 result_json JSON NOT NULL,
 created_at TIMESTAMP(6) NOT NULL,
 INDEX idx_model_event(model_id,created_at)
) ENGINE=InnoDB;
