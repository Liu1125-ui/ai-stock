CREATE TABLE forecast_user_guard (
 user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY
) ENGINE=InnoDB;
CREATE TABLE forecast_request (
 prediction_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 model_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 instrument_id VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 horizon INT NOT NULL,
 state VARCHAR(16) NOT NULL,
 error_code VARCHAR(80) NULL,
 model_json JSON NOT NULL,
 snapshot_json JSON NULL,
 hidden BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMP(6) NOT NULL,
 expires_at TIMESTAMP(6) NOT NULL,
 UNIQUE KEY uq_forecast_operation(user_id,operation_id),
 INDEX idx_forecast_history(user_id,hidden,created_at,prediction_id),
 CONSTRAINT ck_forecast_state CHECK(state IN ('RUNNING','SUCCEEDED','FAILED')),
 CONSTRAINT ck_forecast_horizon CHECK(horizon>0),
 CONSTRAINT ck_forecast_snapshot CHECK((state='SUCCEEDED' AND snapshot_json IS NOT NULL AND error_code IS NULL) OR (state<>'SUCCEEDED' AND snapshot_json IS NULL))
) ENGINE=InnoDB;
