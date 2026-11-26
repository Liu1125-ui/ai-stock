-- Selector provenance is required to bind each later evidence read to its originally selected evidence IDs.
ALTER TABLE market_version_use_bundle ADD COLUMN selector_json JSON NULL;
CREATE TABLE market_version_use_request (
 request_id CHAR(36) PRIMARY KEY,
 bundle_id CHAR(36) NOT NULL,
 request_hash CHAR(64) NOT NULL,
 created_at DATETIME(3) NOT NULL,
 FOREIGN KEY(bundle_id) REFERENCES market_version_use_bundle(bundle_id),
 CHECK(CHAR_LENGTH(request_hash)=64)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
