ALTER TABLE registration_challenge
 DROP CHECK registration_challenge_chk_1,
 ADD COLUMN owner_user_id CHAR(36) NULL,
 ADD COLUMN credential_version BIGINT NULL,
 ADD CONSTRAINT chk_verification_challenge_owner CHECK (
    (purpose = 'REGISTER' AND owner_user_id IS NULL AND credential_version IS NULL)
    OR (purpose = 'EMAIL_CHANGE' AND owner_user_id IS NOT NULL AND credential_version IS NOT NULL AND credential_version >= 1)
 ),
 ADD INDEX ix_verification_challenge_owner (owner_user_id,purpose);
