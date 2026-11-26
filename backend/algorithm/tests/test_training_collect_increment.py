from datetime import date, timedelta
import hashlib
import json
from types import SimpleNamespace

import pytest

from app.forecast.provider import FIELDS
from app.training.collect_increment import collect
from app.training.increment import publish

START = date(2026, 9, 17)
DAYS = [START + timedelta(days=i) for i in range(8)]
SESSIONS = [day for day in DAYS if day.weekday() < 5]
STOCK = "SH600030"


class Result:
    error_code = "0"

    def __init__(self, fields, rows):
        self.fields = fields.split(",")
        self.rows = iter(rows)

    def next(self):
        self.row = next(self.rows, None)
        return self.row is not None

    def get_row_data(self):
        return self.row


class Source:
    def __init__(self, broken=None):
        self.broken = broken
        self.logged_out = False

    def login(self):
        return SimpleNamespace(error_code="10002007" if self.broken == "login" else "0")

    def logout(self):
        self.logged_out = True

    def query_trade_dates(self, **kwargs):
        return Result("calendar_date,is_trading_day",
                      [[str(day), str(int(day in SESSIONS))] for day in DAYS])

    def query_history_k_data_plus(self, code, fields, **kwargs):
        flag = kwargs["adjustflag"]
        sessions = SESSIONS[:-1] if self.broken == "unpaired" and flag == "1" else SESSIONS
        rows = [[str(day), code, "10", "12", "9", "11" if flag == "3" else "22", "100", "1050", flag, "1"]
                for day in sessions]
        return Result(FIELDS, rows)


def archive(tmp_path):
    base = tmp_path / "base"
    base.mkdir()
    header = "date,open,high,low,close,volume,amount,factor,tradestatus"
    raw = ("\n".join([header, *[f"{day},10,12,9,11,100,1050,2,1" for day in SESSIONS[:4]]]) + "\n").encode()
    (base / f"{STOCK}_canonical.csv").write_bytes(raw)
    old_index = base / "index.json"
    old_index.write_text(json.dumps({"source": "Baostock, fixture", "features": [
        {"stock": STOCK, "raw_sha256": hashlib.sha256(raw).hexdigest()}]}))
    return base, old_index, hashlib.sha256(old_index.read_bytes()).hexdigest()


def test_source_output_can_be_merged_using_existing_archive_contract(tmp_path):
    base, old_index, old_hash = archive(tmp_path)
    sdk = Source()
    out = tmp_path / "delta"
    receipt = collect(base, old_index, old_hash, START, DAYS[-1], out, sdk=sdk)
    merged = tmp_path / "merged"
    report = publish(base, old_index, old_hash, out, out/"index.json", receipt["indexHash"],
                     out/"calendar.json", receipt["calendarHash"], SESSIONS[-1], merged)
    assert report["updatedStocks"] == 1
    assert (merged / f"{STOCK}_canonical.csv").read_text().splitlines()[-1].startswith(str(SESSIONS[-1]))
    assert sdk.logged_out


@pytest.mark.parametrize("broken,reason", [("unpaired", "Unpaired"), ("login", "SOURCE_")])
def test_bad_source_never_publishes(tmp_path, broken, reason):
    base, old_index, old_hash = archive(tmp_path)
    out = tmp_path / "delta"
    with pytest.raises(ValueError, match=reason):
        collect(base, old_index, old_hash, START, DAYS[-1], out, sdk=Source(broken))
    assert not out.exists()
