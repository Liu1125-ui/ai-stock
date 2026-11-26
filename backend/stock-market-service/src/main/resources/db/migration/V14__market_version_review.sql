CREATE TABLE market_version_review (
 version_id CHAR(36) PRIMARY KEY,
 revision BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY(version_id) REFERENCES market_data_version(version_id),
 CHECK(revision>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE market_version_review_operation (
 operation_id CHAR(36) PRIMARY KEY,
 version_id CHAR(36) NOT NULL,
 actor_user_id CHAR(36) NOT NULL,
 expected_revision BIGINT NOT NULL,
 revision BIGINT NOT NULL,
 review_state VARCHAR(24) NOT NULL,
 note VARCHAR(300) NOT NULL,
 created_at DATETIME(3) NOT NULL,
 UNIQUE KEY uq_market_review_revision(version_id,revision),
 FOREIGN KEY(version_id) REFERENCES market_version_review(version_id),
 CHECK(revision=expected_revision+1 AND expected_revision>=0),
 CHECK(review_state IN ('UNREVIEWED','REVIEWED','NEEDS_REVIEW')),
 CHECK(CHAR_LENGTH(note)>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
