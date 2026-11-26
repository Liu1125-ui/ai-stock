-- Permission to start new calculations is separate from immutable publication facts.
CREATE TABLE market_version_lifecycle (
 version_id CHAR(36) PRIMARY KEY,
 use_state VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
 revision BIGINT NOT NULL DEFAULT 0,
 cycle_id CHAR(36) NULL,
 updated_at DATETIME(3) NOT NULL,
 FOREIGN KEY(version_id) REFERENCES market_data_version(version_id),
 CHECK(revision>=0),
 CHECK(use_state IN ('ENABLED','DRAINING','DISABLED')),
 CHECK((use_state='ENABLED' AND cycle_id IS NULL) OR (use_state IN ('DRAINING','DISABLED') AND cycle_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE market_version_use_bundle (
 bundle_id CHAR(36) PRIMARY KEY,
 owner VARCHAR(16) NOT NULL,
 purpose VARCHAR(40) NOT NULL,
 owner_operation_id CHAR(36) NOT NULL,
 generation BIGINT NOT NULL,
 selector_hash CHAR(64) NOT NULL,
 versions_hash CHAR(64) NOT NULL,
 version_ids_json JSON NOT NULL,
 state VARCHAR(16) NOT NULL,
 created_at DATETIME(3) NOT NULL,
 last_checked_at DATETIME(3) NULL,
 released_at DATETIME(3) NULL,
 terminal_proof_hash CHAR(64) NULL,
 UNIQUE KEY uq_market_use_identity(owner,purpose,owner_operation_id,generation),
 KEY ix_market_use_bundle_reconcile(state,last_checked_at,bundle_id),
 CHECK(generation>0),
 CHECK((owner='prediction' AND purpose IN ('FORECAST','STOCK_ANALYSIS','COMPARISON','PORTFOLIO_VALUATION','PREDICTION_VERIFICATION'))
    OR (owner='notification' AND purpose='ALERT_EVALUATION')
    OR (owner='market' AND purpose IN ('FORECAST_INPUT_PUBLISH','COMPARISON_EVIDENCE_PUBLISH','ALERT_EVIDENCE_PUBLISH','REALIZATION_EVIDENCE_PUBLISH'))),
 CHECK(JSON_TYPE(version_ids_json)='ARRAY' AND JSON_LENGTH(version_ids_json) BETWEEN 1 AND 100),
 CHECK(state IN ('ACTIVE','RELEASED')),
 CHECK((state='ACTIVE' AND released_at IS NULL AND terminal_proof_hash IS NULL)
    OR (state='RELEASED' AND released_at IS NOT NULL AND terminal_proof_hash IS NOT NULL)),
 CHECK(CHAR_LENGTH(selector_hash)=64 AND CHAR_LENGTH(versions_hash)=64),
 CHECK(terminal_proof_hash IS NULL OR CHAR_LENGTH(terminal_proof_hash)=64)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Owner identity is normalized through bundle_id, avoiding contradictory duplicate identity fields.
CREATE TABLE market_version_use (
 use_id CHAR(36) PRIMARY KEY,
 bundle_id CHAR(36) NOT NULL,
 version_id CHAR(36) NOT NULL,
 state VARCHAR(16) NOT NULL,
 created_at DATETIME(3) NOT NULL,
 last_checked_at DATETIME(3) NULL,
 released_at DATETIME(3) NULL,
 terminal_proof_hash CHAR(64) NULL,
 UNIQUE KEY uq_market_use_bundle_version(bundle_id,version_id),
 KEY ix_market_use_version_active(version_id,state,use_id),
 FOREIGN KEY(bundle_id) REFERENCES market_version_use_bundle(bundle_id),
 FOREIGN KEY(version_id) REFERENCES market_version_lifecycle(version_id),
 CHECK(state IN ('ACTIVE','RELEASED')),
 CHECK((state='ACTIVE' AND released_at IS NULL AND terminal_proof_hash IS NULL)
    OR (state='RELEASED' AND released_at IS NOT NULL AND terminal_proof_hash IS NOT NULL)),
 CHECK(terminal_proof_hash IS NULL OR CHAR_LENGTH(terminal_proof_hash)=64)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE market_version_action (
 operation_id CHAR(36) PRIMARY KEY,
 actor_user_id CHAR(36) NOT NULL,
 version_id CHAR(36) NOT NULL,
 action VARCHAR(16) NOT NULL,
 expected_revision BIGINT NOT NULL,
 request_hash CHAR(64) NOT NULL,
 reason VARCHAR(300) NOT NULL,
 cycle_id CHAR(36) NULL,
 state VARCHAR(16) NOT NULL,
 before_state VARCHAR(16) NOT NULL,
 after_state VARCHAR(16) NOT NULL,
 error_code VARCHAR(64) NULL,
 created_at DATETIME(3) NOT NULL,
 finished_at DATETIME(3) NULL,
 KEY ix_market_version_action_history(version_id,created_at,operation_id),
 KEY ix_market_version_action_cycle(version_id,cycle_id,state),
 FOREIGN KEY(version_id) REFERENCES market_version_lifecycle(version_id),
 CHECK(action IN ('DISABLE','RESTORE')),
 CHECK(expected_revision>=0),
 CHECK(CHAR_LENGTH(request_hash)=64 AND CHAR_LENGTH(reason) BETWEEN 1 AND 300),
 CHECK(state IN ('WAITING','COMPLETED','REJECTED','SUPERSEDED')),
 CHECK(before_state IN ('ENABLED','DRAINING','DISABLED') AND after_state IN ('ENABLED','DRAINING','DISABLED')),
 CHECK((state='WAITING' AND action='DISABLE' AND after_state='DRAINING' AND cycle_id IS NOT NULL AND finished_at IS NULL)
    OR (state IN ('COMPLETED','REJECTED','SUPERSEDED') AND finished_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE market_version_action_event (
 event_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 operation_id CHAR(36) NOT NULL,
 event_type VARCHAR(16) NOT NULL,
 lifecycle_revision BIGINT NOT NULL,
 use_state VARCHAR(16) NOT NULL,
 cycle_id CHAR(36) NULL,
 created_at DATETIME(3) NOT NULL,
 UNIQUE KEY uq_market_version_action_event(operation_id,event_type),
 FOREIGN KEY(operation_id) REFERENCES market_version_action(operation_id),
 CHECK(event_type IN ('ACCEPTED','COMPLETED','REJECTED','SUPERSEDED')),
 CHECK(lifecycle_revision>=0),
 CHECK(use_state IN ('ENABLED','DRAINING','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Backfill only supported, published data. No head, price, or forecast-input row is rewritten.
INSERT INTO market_version_lifecycle(version_id,use_state,revision,updated_at)
 SELECT version_id,'ENABLED',0,UTC_TIMESTAMP(3)
 FROM market_data_version
 WHERE provider='baostock' AND price_basis='RAW' AND status='AVAILABLE';
