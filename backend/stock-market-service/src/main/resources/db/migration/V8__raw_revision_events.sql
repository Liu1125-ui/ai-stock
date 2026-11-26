CREATE TABLE market_revision_sequence(singleton_id TINYINT PRIMARY KEY,last_id BIGINT NOT NULL,CHECK(singleton_id=1),CHECK(last_id>=0)) ENGINE=InnoDB;
INSERT INTO market_revision_sequence VALUES(1,0);
CREATE TABLE market_revision_event(event_id BIGINT PRIMARY KEY,instrument_id VARCHAR(64) NOT NULL,raw_version_id CHAR(36) NOT NULL,created_at DATETIME(3) NOT NULL,FOREIGN KEY(raw_version_id) REFERENCES market_data_version(version_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
INSERT INTO market_revision_event(event_id,instrument_id,raw_version_id,created_at) SELECT ROW_NUMBER() OVER(ORDER BY instrument_id),instrument_id,version_id,CURRENT_TIMESTAMP(3) FROM instrument_data_head WHERE provider='baostock' AND price_basis='RAW' AND version_id IS NOT NULL;
UPDATE market_revision_sequence SET last_id=(SELECT COALESCE(MAX(event_id),0) FROM market_revision_event) WHERE singleton_id=1;
