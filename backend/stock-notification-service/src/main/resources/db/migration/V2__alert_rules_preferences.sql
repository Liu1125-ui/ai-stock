CREATE TABLE alert_user_guard(user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY) ENGINE=InnoDB;
CREATE TABLE alert_rule(
 rule_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 instrument_id VARCHAR(16) NOT NULL,condition_code VARCHAR(20) NOT NULL,threshold_value DECIMAL(20,6) NOT NULL,
 enabled BOOLEAN NOT NULL,revision BIGINT NOT NULL,effective_after_date DATE NOT NULL,boundary_raw_version CHAR(36) NOT NULL,
 boundary_json LONGTEXT NOT NULL,created_at TIMESTAMP(6) NOT NULL,updated_at TIMESTAMP(6) NOT NULL,
 KEY ix_alert_owner(user_id,created_at,rule_id),CHECK(threshold_value>0),CHECK(revision>=0),CHECK(JSON_VALID(boundary_json)),
 CHECK(condition_code IN('PRICE_ABOVE','PRICE_BELOW','DAILY_RISE','DAILY_FALL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE alert_rule_request(user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,request_hash CHAR(64) NOT NULL,response_json LONGTEXT NOT NULL,created_at TIMESTAMP(6) NOT NULL,PRIMARY KEY(user_id,request_id),CHECK(JSON_VALID(response_json))) ENGINE=InnoDB;
CREATE TABLE notification_preference(user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,email_enabled BOOLEAN NOT NULL DEFAULT FALSE,revision BIGINT NOT NULL DEFAULT 0,updated_at TIMESTAMP(6) NOT NULL,CHECK(revision>=0)) ENGINE=InnoDB;
