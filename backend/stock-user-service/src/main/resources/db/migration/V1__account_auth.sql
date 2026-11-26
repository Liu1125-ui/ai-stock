CREATE TABLE user_account (
 user_id CHAR(36) PRIMARY KEY,
 email VARCHAR(254) NOT NULL,
 email_normalized VARCHAR(254) NOT NULL,
 email_verified_at DATETIME(3) NOT NULL,
 email_version BIGINT NOT NULL DEFAULT 1,
 password_hash VARCHAR(100) NOT NULL,
 role_code VARCHAR(16) NOT NULL,
 status VARCHAR(16) NOT NULL,
 nickname VARCHAR(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 avatar_uri VARCHAR(512) CHARACTER SET utf8mb4 NULL,
 credential_version BIGINT NOT NULL DEFAULT 1,
 revision BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL,
 last_active_at DATETIME(3) NULL,
 UNIQUE KEY uk_user_email (email_normalized),
 KEY ix_user_status (status,user_id), KEY ix_user_role (role_code,user_id),
 CHECK (role_code IN ('USER','ADMIN')), CHECK (status IN ('ENABLED','DISABLED')),
 CHECK (email_version >= 1 AND credential_version >= 1 AND revision >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=ascii COLLATE=ascii_bin;

CREATE TABLE registration_challenge (
 challenge_id CHAR(36) PRIMARY KEY,
 email_normalized VARCHAR(254) NOT NULL,
 code_digest CHAR(64) NOT NULL,
 digest_key_id VARCHAR(32) NOT NULL,
 purpose VARCHAR(16) NOT NULL,
 expires_at DATETIME(3) NOT NULL,
 failed_attempts INT NOT NULL DEFAULT 0,
 consumed_at DATETIME(3) NULL,
 invalidated_at DATETIME(3) NULL,
 created_at DATETIME(3) NOT NULL,
 KEY ix_challenge_email (email_normalized,created_at), KEY ix_challenge_expiry (expires_at),
 CHECK (purpose = 'REGISTER'), CHECK (failed_attempts >= 0), CHECK (expires_at > created_at)
) ENGINE=InnoDB DEFAULT CHARSET=ascii COLLATE=ascii_bin;

CREATE TABLE registration_dispatch (
 request_id CHAR(36) PRIMARY KEY,
 challenge_id CHAR(36) NOT NULL UNIQUE,
 state VARCHAR(16) NOT NULL,
 attempt_count INT NOT NULL DEFAULT 0,
 next_attempt_at DATETIME(3) NOT NULL,
 lease_until DATETIME(3) NULL,
 claim_version BIGINT NOT NULL DEFAULT 0,
 payload_ciphertext BLOB NULL, payload_nonce BINARY(12) NULL, payload_key_id VARCHAR(32) NULL,
 expires_at DATETIME(3) NOT NULL,
 last_error_code VARCHAR(64) NULL,
 created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 FOREIGN KEY (challenge_id) REFERENCES registration_challenge(challenge_id),
 KEY ix_dispatch_pending (state,next_attempt_at), KEY ix_dispatch_expiry (expires_at),
 CHECK (state IN ('PENDING','CLAIMED','ACCEPTED','FAILED','EXPIRED')),
 CHECK (attempt_count >= 0 AND claim_version >= 0),
 CHECK ((payload_ciphertext IS NULL AND payload_nonce IS NULL AND payload_key_id IS NULL)
 OR (payload_ciphertext IS NOT NULL AND payload_nonce IS NOT NULL AND payload_key_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=ascii COLLATE=ascii_bin;

CREATE TABLE registration_receipt (
 registration_request_id CHAR(36) PRIMARY KEY,
 challenge_id CHAR(36) NOT NULL UNIQUE,
 user_id CHAR(36) NOT NULL,
 public_request_digest CHAR(64) NOT NULL,
 created_at DATETIME(3) NOT NULL,
 FOREIGN KEY (challenge_id) REFERENCES registration_challenge(challenge_id),
 FOREIGN KEY (user_id) REFERENCES user_account(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=ascii COLLATE=ascii_bin;

CREATE TABLE login_session (
 session_id CHAR(36) PRIMARY KEY,
 user_id CHAR(36) NOT NULL,
 token_hash CHAR(64) NOT NULL UNIQUE,
 credential_version BIGINT NOT NULL,
 expires_at DATETIME(3) NOT NULL,
 revoked_at DATETIME(3) NULL,
 created_at DATETIME(3) NOT NULL,
 FOREIGN KEY (user_id) REFERENCES user_account(user_id),
 KEY ix_session_user (user_id,revoked_at), KEY ix_session_expiry (expires_at),
 CHECK (credential_version >= 1), CHECK (expires_at > created_at)
) ENGINE=InnoDB DEFAULT CHARSET=ascii COLLATE=ascii_bin;

CREATE TABLE experiment_grant (
 grant_id CHAR(36) PRIMARY KEY,
 user_id CHAR(36) NOT NULL,
 granted_by CHAR(36) NOT NULL,
 state VARCHAR(16) NOT NULL,
 granted_at DATETIME(3) NOT NULL,
 revoked_at DATETIME(3) NULL,
 active_user_id CHAR(36) GENERATED ALWAYS AS (CASE WHEN state='ACTIVE' THEN user_id ELSE NULL END) STORED,
 UNIQUE KEY uk_one_active_grant (active_user_id),
 FOREIGN KEY (user_id) REFERENCES user_account(user_id),
 FOREIGN KEY (granted_by) REFERENCES user_account(user_id),
 CHECK (state IN ('ACTIVE','REVOKED')),
 CHECK ((state='ACTIVE' AND revoked_at IS NULL) OR (state='REVOKED' AND revoked_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=ascii COLLATE=ascii_bin;

CREATE TABLE user_audit (
 audit_id CHAR(36) PRIMARY KEY,
 operator_id CHAR(36) NULL,
 target_id CHAR(36) NOT NULL,
 action VARCHAR(32) NOT NULL,
 result VARCHAR(16) NOT NULL,
 trace_id VARCHAR(64) NOT NULL,
 occurred_at DATETIME(3) NOT NULL,
 FOREIGN KEY (operator_id) REFERENCES user_account(user_id),
 FOREIGN KEY (target_id) REFERENCES user_account(user_id),
 KEY ix_audit_target (target_id,occurred_at), KEY ix_audit_operator (operator_id,occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=ascii COLLATE=ascii_bin;

CREATE TABLE auth_rate_bucket (
 bucket_key CHAR(64) PRIMARY KEY,
 scope VARCHAR(32) NOT NULL,
 window_started_at DATETIME(3) NOT NULL,
 window_ends_at DATETIME(3) NOT NULL,
 request_count INT NOT NULL DEFAULT 0,
 last_request_at DATETIME(3) NULL,
 revision BIGINT NOT NULL DEFAULT 0,
 KEY ix_rate_expiry (window_ends_at),
 CHECK (request_count >= 0 AND revision >= 0), CHECK (window_ends_at > window_started_at)
) ENGINE=InnoDB DEFAULT CHARSET=ascii COLLATE=ascii_bin;

CREATE TABLE bootstrap_guard (
 guard_key VARCHAR(32) PRIMARY KEY,
 created_at DATETIME(3) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=ascii COLLATE=ascii_bin;
INSERT INTO bootstrap_guard(guard_key,created_at) VALUES ('ADMIN_INIT',UTC_TIMESTAMP(3));
