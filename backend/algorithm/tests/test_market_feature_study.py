from datetime import date, timedelta
from unittest import TestCase
import numpy as np
from app.training.market_feature_study import market_features, window_with_market


class MarketFeatureStudyTest(TestCase):
    def test_future_index_close_does_not_change_features_at_origin(self):
        days=[(date(2025,1,1)+timedelta(days=i)).isoformat() for i in range(23)]
        closes=[100+i*i for i in range(23)]
        before=market_features(days,closes)
        after=market_features(days,closes[:22]+[99999])
        self.assertNotIn(days[19],before)
        self.assertAlmostEqual(before[days[21]][0],closes[21]/closes[20]-1)
        np.testing.assert_array_equal(before[days[21]],after[days[21]])
        self.assertNotEqual(before[days[22]],after[days[22]])

    def test_window_requires_exact_market_dates(self):
        raw=np.ones((2,6),dtype=np.float32)
        expanded=window_with_market({"a":(.1,.2),"b":(.3,.4)},raw,["b","a"])
        np.testing.assert_allclose(expanded[:,-2:],[[.3,.4],[.1,.2]])
        with self.assertRaises(ValueError):
            window_with_market({"a":(.1,.2)},raw,["a","b"])
