CREATE TABLE alert_scan_cursor (
 kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 completed_event_id BIGINT NOT NULL,event_id BIGINT NULL,instrument_id VARCHAR(16) NULL,event_raw_version CHAR(36) NULL,
 round_id CHAR(36) NULL,cutoff_date DATE NULL,expected_date DATE NULL,after_id CHAR(36) NULL,
 revision BIGINT NOT NULL,next_at TIMESTAMP(6) NOT NULL,completed_at TIMESTAMP(6) NULL,updated_at TIMESTAMP(6) NOT NULL,last_reason VARCHAR(64) NULL,
 CHECK(kind IN('EVENT','RECONCILE')),CHECK(completed_event_id>=0),CHECK(revision>=0),
 CHECK((round_id IS NULL AND cutoff_date IS NULL AND expected_date IS NULL AND after_id IS NULL) OR (round_id IS NOT NULL AND cutoff_date IS NOT NULL AND expected_date IS NOT NULL AND expected_date<=cutoff_date)),
 CHECK((kind='RECONCILE' AND event_id IS NULL AND instrument_id IS NULL AND event_raw_version IS NULL) OR (kind='EVENT' AND ((round_id IS NULL AND event_id IS NULL AND instrument_id IS NULL AND event_raw_version IS NULL) OR (round_id IS NOT NULL AND event_id>completed_event_id AND instrument_id IS NOT NULL AND event_raw_version IS NOT NULL))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE alert_calendar_refresh (
 id INT PRIMARY KEY,request_day DATE NULL,operation_id CHAR(36) NULL,range_start DATE NULL,range_end DATE NULL,
 owner_token CHAR(36) NULL,lease_until TIMESTAMP(6) NULL,success_day DATE NULL,next_at TIMESTAMP(6) NOT NULL,revision BIGINT NOT NULL,last_reason VARCHAR(64) NULL,
 CHECK(id=1),CHECK(revision>=0),CHECK((owner_token IS NULL AND lease_until IS NULL) OR (owner_token IS NOT NULL AND lease_until IS NOT NULL)),
 CHECK((request_day IS NULL AND operation_id IS NULL AND range_start IS NULL AND range_end IS NULL) OR (request_day IS NOT NULL AND operation_id IS NOT NULL AND range_start IS NOT NULL AND range_end IS NOT NULL AND range_start<=range_end))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE alert_scan_budget(id INT PRIMARY KEY,CHECK(id=1)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
