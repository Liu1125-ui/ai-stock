CREATE TABLE instrument (
    instrument_id VARCHAR(64) PRIMARY KEY,
    ticker VARCHAR(32) NOT NULL UNIQUE,
    display_name VARCHAR(128) NOT NULL,
    demo_only BOOLEAN NOT NULL
);
