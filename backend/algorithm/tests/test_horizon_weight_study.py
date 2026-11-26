from unittest import TestCase

import torch

from app.training.five_day import forecast_loss, loss_weights


class HorizonWeightTest(TestCase):
    def test_double_weight_increases_last_day_contribution(self):
        predicted=torch.tensor([[1., 1., 1., 1., 2.]])
        actual=torch.ones_like(predicted)
        self.assertAlmostEqual(forecast_loss(predicted,actual,None).item(),1/5)
        weights=loss_weights((1,1,1,1,2),5)
        self.assertAlmostEqual(forecast_loss(predicted,actual,weights).item(),2/6)

    def test_equal_weights_preserve_mean_squared_error(self):
        predicted=torch.tensor([[1.,2.,3.,4.,5.]])
        actual=torch.ones_like(predicted)
        self.assertEqual(forecast_loss(predicted,actual,loss_weights((1,)*5,5)).item(),
                         forecast_loss(predicted,actual,None).item())

    def test_invalid_loss_weights_fail_early(self):
        for weights in ((1,2),(1,1,1,1,0),(1,1,1,1,float("inf"))):
            with self.assertRaises(ValueError):
                loss_weights(weights,5)
