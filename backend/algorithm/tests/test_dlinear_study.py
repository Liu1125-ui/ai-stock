import numpy as np
from unittest import TestCase
from app.training.dlinear_study import design, fit, predict, metrics


class DLinearStudyTest(TestCase):
    def test_constant_prices_have_only_a_trend_component(self):
        raw=np.full((2,60,6),10.0)
        parts=design(raw, np.zeros(6), np.ones(6))
        np.testing.assert_allclose(parts[:,:360],0)
        np.testing.assert_allclose(parts[:,360:],10)

    def test_fitting_can_learn_a_constant_five_day_ratio(self):
        features=np.zeros((6,4))
        labels=np.tile([1.01,1.02,1.03,1.04,1.05],(6,1))
        result=predict(features,fit(features,labels,1))
        np.testing.assert_allclose(result,labels)
        np.testing.assert_allclose(metrics(result,labels)["maeByDay"],[0]*5,atol=1e-14)

    def test_rejects_predictions_with_invalid_prices(self):
        with self.assertRaises(ValueError):
            metrics(np.zeros((2,5)),np.ones((2,5)))
