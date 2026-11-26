CREATE TABLE forecast_input_version (
 input_version_id CHAR(36) PRIMARY KEY, raw_version_id CHAR(36) NOT NULL, instrument_id VARCHAR(64) NOT NULL,
 provider VARCHAR(20) NOT NULL, definition_code VARCHAR(64) NOT NULL, content_hash CHAR(64) NOT NULL,
 calendar_hash CHAR(64) NOT NULL, source_payload_hash CHAR(64) NOT NULL, fetched_at DATETIME(3) NOT NULL,
 origin_date DATE NOT NULL, calendar_start DATE NOT NULL, calendar_end DATE NOT NULL, status VARCHAR(16) NOT NULL,
 UNIQUE KEY uk_forecast_input_content(raw_version_id,definition_code,content_hash),
 FOREIGN KEY(raw_version_id) REFERENCES market_data_version(version_id),
 CHECK(status IN ('AVAILABLE','UNAVAILABLE')), CHECK(calendar_start<=origin_date AND calendar_end>origin_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE forecast_input_adjustment (
 input_version_id CHAR(36) NOT NULL, trade_date DATE NOT NULL, backward_close DECIMAL(20,6) NOT NULL,
 source_adjustment_ratio DECIMAL(24,12) NOT NULL, PRIMARY KEY(input_version_id,trade_date),
 FOREIGN KEY(input_version_id) REFERENCES forecast_input_version(input_version_id),
 CHECK(backward_close>0 AND source_adjustment_ratio>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE forecast_input_calendar (
 input_version_id CHAR(36) NOT NULL, calendar_date DATE NOT NULL, is_open BOOLEAN NOT NULL,
 PRIMARY KEY(input_version_id,calendar_date), FOREIGN KEY(input_version_id) REFERENCES forecast_input_version(input_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE forecast_input_operation (
 operation_id VARCHAR(128) PRIMARY KEY, request_hash CHAR(64) NOT NULL, input_version_id CHAR(36) NULL,
 created_at DATETIME(3) NOT NULL, FOREIGN KEY(input_version_id) REFERENCES forecast_input_version(input_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
