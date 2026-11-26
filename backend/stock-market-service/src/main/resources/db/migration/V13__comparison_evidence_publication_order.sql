-- Keep immutable evidence content; new successful operations may reactivate reused content.
ALTER TABLE comparison_evidence_operation
 ADD COLUMN publication_sequence BIGINT NULL,
 ADD UNIQUE KEY uk_comparison_publication_sequence(publication_sequence);
UPDATE comparison_evidence_operation o
 JOIN (SELECT operation_id, ROW_NUMBER() OVER (ORDER BY created_at,operation_id) AS seq
       FROM comparison_evidence_operation) ranked ON ranked.operation_id=o.operation_id
 SET o.publication_sequence=ranked.seq;
ALTER TABLE comparison_evidence_operation
 MODIFY COLUMN publication_sequence BIGINT NOT NULL AUTO_INCREMENT,
 ADD KEY ix_comparison_evidence_publication(evidence_id,publication_sequence);
