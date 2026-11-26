ALTER TABLE alert_evaluation_operation
  ADD COLUMN checked_trade_date DATE NULL,
  ADD KEY ix_alert_latest_check(owner_id,rule_id,rule_revision,created_at,operation_id);
