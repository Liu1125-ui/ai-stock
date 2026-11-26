ALTER TABLE forecast_request
 ADD COLUMN execution_generation BIGINT NOT NULL DEFAULT 1,
 ADD COLUMN fencing_revision BIGINT NOT NULL DEFAULT 1,
 ADD COLUMN finished_at DATETIME(3) NULL,
 ADD CONSTRAINT ck_forecast_generation CHECK (execution_generation > 0),
 ADD CONSTRAINT ck_forecast_fence CHECK (fencing_revision > 0);
ALTER TABLE forecast_verification_operation
 ADD COLUMN execution_generation BIGINT NOT NULL DEFAULT 1,
 ADD COLUMN fencing_revision BIGINT NOT NULL DEFAULT 1,
 ADD CONSTRAINT ck_verification_generation CHECK (execution_generation > 0),
 ADD CONSTRAINT ck_verification_fence CHECK (fencing_revision > 0);
CREATE TABLE prediction_market_use (
 owner_operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 purpose VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 execution_generation BIGINT NOT NULL,
 request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 instrument_id VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 registration_started BOOLEAN NOT NULL DEFAULT FALSE,
 bundle_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL UNIQUE,
 raw_version_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 receipt_json JSON NULL,
 completion_acked BOOLEAN NOT NULL DEFAULT FALSE,
 created_at DATETIME(3) NOT NULL,
 last_checked_at DATETIME(3) NOT NULL,
 CHECK (purpose IN ('FORECAST','PREDICTION_VERIFICATION')),
 CHECK (execution_generation > 0),
 CHECK ((bundle_id IS NULL AND receipt_json IS NULL AND raw_version_id IS NULL) OR (bundle_id IS NOT NULL AND receipt_json IS NOT NULL AND raw_version_id IS NOT NULL)),
 CHECK (completion_acked = FALSE OR bundle_id IS NOT NULL),
 KEY ix_prediction_use_pending(completion_acked,registration_started,last_checked_at,owner_operation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
