"""Synthetic metadata tests; these values are not model evaluation results."""
import importlib.util
import math
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("prepare_models", Path(__file__).with_name("prepare-model-registrations.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class PreparationTests(unittest.TestCase):
    def sample(self):
        return ({"seed": 42, "epoch": 3, "artifactId": "candidate", "manifestHash": "a" * 64},
                {"seed": 42, "cutoff": "2023-12-31", "mode": "absolute", "window": "one_year", "policies": {
                    "rmse6p2": {"epoch": 3, "validation": {"mae": .032}, "validation_baseline": {"mae": .033},
                    "evaluation": {"year2025": {"mae": .023}, "year2026": {"mae": .031, "direction": .47}}}}})
    def test_metrics_are_strings_and_correct_years_not_rounded_percentages(self):
        artifact, report = self.sample()
        result = module.registration(artifact, report, b"original report bytes")
        self.assertEqual(result["evaluation"]["validationMae"], "0.032000000000")
        self.assertEqual(result["evaluation"]["mae2026"], "0.031000000000")
        self.assertEqual(len(result["evaluation"]["sourceHash"]), 64)
    def test_different_seed_epoch_or_cutoff_is_rejected(self):
        for field, value in (("seed", 43), ("cutoff", "2024-12-31"), ("mode", "relative"), ("window", "two_years")):
            artifact, report = self.sample()
            report[field] = value
            with self.assertRaises(ValueError): module.registration(artifact, report, b"bytes")
        artifact, report = self.sample()
        report["policies"]["rmse6p2"]["epoch"] = 4
        with self.assertRaises(ValueError): module.registration(artifact, report, b"bytes")
    def test_nonfinite_negative_and_out_of_range_metrics_are_rejected(self):
        for value in (math.nan, math.inf, -.01, 20):
            with self.assertRaises(ValueError): module.metric(value, 10)
        with self.assertRaises(ValueError): module.metric(1.01, 1)

if __name__ == "__main__": unittest.main()
