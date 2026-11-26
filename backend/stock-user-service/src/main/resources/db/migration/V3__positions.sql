CREATE TABLE position_book (
 user_id CHAR(36) PRIMARY KEY, revision BIGINT NOT NULL DEFAULT 0, updated_at DATETIME(3) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES user_account(user_id), CHECK(revision>=0)
) ENGINE=InnoDB DEFAULT CHARSET=ascii COLLATE=ascii_bin;
CREATE TABLE position (
 position_id CHAR(36) PRIMARY KEY, user_id CHAR(36) NOT NULL, instrument_id VARCHAR(8) NOT NULL,
 quantity BIGINT NOT NULL, average_cost DECIMAL(20,6) NOT NULL, revision BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_position_owner_instrument(user_id,instrument_id),
 FOREIGN KEY(user_id) REFERENCES user_account(user_id), CHECK(quantity>=1), CHECK(average_cost>=0), CHECK(revision>=1)
) ENGINE=InnoDB DEFAULT CHARSET=ascii COLLATE=ascii_bin;
CREATE TABLE position_operation_receipt (
 user_id CHAR(36) NOT NULL, request_id CHAR(36) NOT NULL, action VARCHAR(12) NOT NULL,
 payload_hash CHAR(64) NOT NULL, state VARCHAR(16) NOT NULL, http_status INT NOT NULL,
 error_code VARCHAR(64) NOT NULL, message VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 position_id CHAR(36) NULL, book_revision BIGINT NOT NULL, result_json JSON NOT NULL,
 created_at DATETIME(3) NOT NULL, PRIMARY KEY(user_id,request_id),
 FOREIGN KEY(user_id) REFERENCES user_account(user_id),
 CHECK(action IN ('CREATE','UPDATE','DELETE')), CHECK(state IN ('CREATED','UPDATED','DELETED','UNCHANGED','REJECTED')),
 CHECK(book_revision>=0), CHECK(http_status BETWEEN 200 AND 499)
) ENGINE=InnoDB DEFAULT CHARSET=ascii COLLATE=ascii_bin;
