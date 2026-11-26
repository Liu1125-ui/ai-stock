CREATE TABLE watchlist (
 user_id CHAR(36) NOT NULL, instrument_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_at DATETIME(3) NOT NULL, PRIMARY KEY(user_id,instrument_id),
 KEY ix_watchlist_owner_date(user_id,created_at,instrument_id),
 FOREIGN KEY(user_id) REFERENCES user_account(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=ascii COLLATE=ascii_bin;
