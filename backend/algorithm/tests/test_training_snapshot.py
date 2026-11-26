from datetime import date
import numpy as np
import pytest

from app.training.snapshot import transform

HEADER="date,open,high,low,close,volume,amount,factor,tradestatus\n"


def rows(*dates):
    return (HEADER+"".join(f"{day},10,12,9,11,100,1050,2,{status}\n" for day,status in dates)).encode()


def test_snapshot_adjusts_price_and_volume_and_does_not_fill_suspension():
    data=rows(("2026-09-21",1),("2026-09-22",0),("2026-09-23",1))
    result=transform(data,date(2026,9,23))
    np.testing.assert_allclose(result["base"][0],[20,24,18,22,21,50])
    assert result["valid"].tolist()==[True,False,True]
    assert np.isnan(result["base"][1]).all()
    assert np.isnan(result["targets"][-1]).all()


@pytest.mark.parametrize("dates",[
    (("2026-09-24",1),),
    (("2026-09-22",1),("2026-09-22",1)),
    (("2026-09-22",1),("2026-09-21",1)),
])
def test_snapshot_rejects_future_duplicate_and_out_of_order_rows(dates):
    with pytest.raises(ValueError):
        transform(rows(*dates),date(2026,9,23))
