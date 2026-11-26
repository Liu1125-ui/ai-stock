ALTER TABLE training_dispatch_inbox
    ADD COLUMN checkpoint_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL;
