-- Existing operations already contain their frozen context. New admissions start unprepared.
ALTER TABLE stock_analysis_operation
 ADD COLUMN execution_generation BIGINT NOT NULL DEFAULT 1,
 ADD COLUMN fencing_revision BIGINT NOT NULL DEFAULT 1,
 ADD COLUMN context_ready BOOLEAN NOT NULL DEFAULT TRUE,
 ADD CONSTRAINT ck_analysis_execution_generation CHECK (execution_generation > 0),
 ADD CONSTRAINT ck_analysis_fencing_revision CHECK (fencing_revision > 0);
