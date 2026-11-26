from unittest import TestCase
import numpy as np
from app.training.stock_expansion import select_windows, score_predictions


class ExpansionEvidenceTest(TestCase):
    def test_targets_stay_inside_holdout_and_do_not_overlap(self):
        from datetime import date, timedelta
        days = [(date(2025, 1, 1) + timedelta(days=i)).isoformat() for i in range(74)]
        windows = select_windows(days, days[60], days[73])
        self.assertEqual(windows, [68, 63])
        self.assertLessEqual(windows[1] + 5, windows[0])

    def test_baseline_gain_and_per_horizon_metrics_are_in_ratio_units(self):
        predicted = np.full((2, 5), 1.02)
        actual = np.full((2, 5), 1.03)
        scores = score_predictions(predicted, actual)
        self.assertAlmostEqual(scores["mae"], 0.01)
        self.assertAlmostEqual(scores["baselineMae"], 0.03)
        self.assertAlmostEqual(scores["directionAccuracy"], 1.0)
        self.assertEqual(len(scores["horizonMae"]), 5)

    def test_rejects_invalid_model_results(self):
        with self.assertRaises(ValueError):
            score_predictions(np.full((2, 5), np.nan), np.ones((2, 5)))
