CREATE TABLE alert_trigger (
 trigger_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 owner_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 instrument_id VARCHAR(16) NOT NULL, rule_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 trade_date DATE NOT NULL, rule_revision BIGINT NOT NULL, snapshot_json LONGTEXT NOT NULL, created_at TIMESTAMP(6) NOT NULL,
 UNIQUE KEY uq_alert_daily(owner_id,instrument_id,rule_id,trade_date),KEY ix_trigger_rule(owner_id,rule_id,created_at,trigger_id),CHECK(JSON_VALID(snapshot_json)),CHECK(rule_revision>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE inbox_message (
 message_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 trigger_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 owner_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_at TIMESTAMP(6) NOT NULL,read_at TIMESTAMP(6) NULL,
 KEY ix_inbox_owner(owner_id,created_at,message_id),KEY ix_inbox_unread(owner_id,read_at,created_at,message_id),
 CONSTRAINT fk_inbox_trigger FOREIGN KEY(trigger_id) REFERENCES alert_trigger(trigger_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE alert_evaluation_operation (
 operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,owner_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_hash CHAR(64) NOT NULL,rule_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,rule_revision BIGINT NOT NULL,
 evidence_id CHAR(36) NOT NULL,lease_until TIMESTAMP(6) NOT NULL,state VARCHAR(32) NOT NULL,trigger_id CHAR(36) NULL,reason_code VARCHAR(64) NULL,
 created_at TIMESTAMP(6) NOT NULL,finished_at TIMESTAMP(6) NULL,KEY ix_evaluation_lease(state,lease_until),
 CHECK(rule_revision>=0),CHECK(state IN('RUNNING','TRIGGERED','ALREADY_TRIGGERED','NOT_MET','SKIPPED_RULE_CHANGED','SKIPPED_INACTIVE','SKIPPED_NOT_EFFECTIVE','SKIPPED_STALE','EVIDENCE_UNAVAILABLE','FAILED')),
 CHECK((state IN('TRIGGERED','ALREADY_TRIGGERED') AND trigger_id IS NOT NULL) OR (state NOT IN('TRIGGERED','ALREADY_TRIGGERED') AND trigger_id IS NULL)),
 CHECK((state='RUNNING' AND finished_at IS NULL) OR (state<>'RUNNING' AND finished_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
