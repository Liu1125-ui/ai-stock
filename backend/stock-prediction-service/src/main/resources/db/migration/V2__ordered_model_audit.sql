-- Audit causal order does not depend on wall-clock precision or random UUID order.
ALTER TABLE model_release_event ADD COLUMN event_sequence BIGINT NOT NULL AUTO_INCREMENT UNIQUE;
