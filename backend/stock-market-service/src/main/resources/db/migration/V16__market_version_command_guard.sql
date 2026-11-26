-- A first registration has no bundle row yet. Serialize its identity before resolving any heads.
CREATE TABLE market_version_command_guard (
 guard_key CHAR(64) PRIMARY KEY,
 guard_type VARCHAR(8) NOT NULL,
 created_at DATETIME(3) NOT NULL,
 CHECK(CHAR_LENGTH(guard_key)=64),
 CHECK(guard_type IN ('USE','ACTION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
