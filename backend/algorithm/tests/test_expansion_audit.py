from pathlib import Path
from unittest import TestCase
from unittest.mock import patch

from app.training.expansion_audit import audit_batch, performance_flags


class ExpansionAuditTest(TestCase):
    def test_flags_day_five_and_small_aggregate_improvement(self):
        scores = {"windows": 78, "mae": .0161582, "baselineMae": .0163504,
                  "horizonMae": [.01, .01, .01, .02, .0213676],
                  "horizonBaselineMae": [.02, .02, .02, .03, .0212955]}
        flags = performance_flags(scores)
        self.assertIn("SMALL_BASELINE_GAIN", flags)
        self.assertIn("HORIZON_5_ABOVE_BASELINE", flags)

    def test_isolates_bad_candidate_and_counts_batch_results(self):
        folders = [Path("valid"), Path("invalid"), Path("review")]
        with patch("app.training.expansion_audit.audit_case", side_effect=[
            {"status": "TECHNICAL_PASS"}, ValueError("source differs"),
            {"status": "REVIEW_REQUIRED"},
        ]):
            result = audit_batch(Path("models"), folders)
        self.assertEqual(result["counts"], {"TECHNICAL_PASS": 1, "REVIEW_REQUIRED": 1, "BLOCKED": 1})
        self.assertEqual(result["cases"][1]["case"], "invalid")
        self.assertEqual(result["cases"][1]["reason"], "source differs")
