import numpy as np
import pytest

from app.training.horizon_study import calibrate
from app.training.five_day import target_ratios


def test_targets_use_each_future_close_against_origin():
    rows = np.zeros((22, 6), dtype=np.float32)
    rows[:, 3] = np.arange(22) + 10
    assert target_ratios(rows, 1, 10).tolist() == pytest.approx(np.arange(12, 22) / 11)
    assert target_ratios(rows, 1, 20)[-1] == pytest.approx(31 / 11)


def test_calibration_uses_conservative_empirical_order_per_step():
    errors = np.array([[1, 10], [2, 9], [3, 8], [4, 7], [5, 6]])
    assert calibrate(errors, .8).tolist() == [5, 10]
    with pytest.raises(ValueError):
        calibrate(errors[:2], .8)
