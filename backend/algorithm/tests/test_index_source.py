from datetime import date

import pytest

from app.market.index_source import IndexSource


class Result:
    def __init__(self, fields, rows, error_code="0"):
        self.fields = fields.split(",")
        self.rows = iter(rows)
        self.error_code = error_code

    def next(self):
        self.current = next(self.rows, None)
        return self.current is not None

    def get_row_data(self):
        return self.current


class SDK:
    def __init__(self):
        self.calendar = [("2026-11-13", "1"), ("2026-11-14", "0"),
                         ("2026-11-15", "0"), ("2026-11-16", "1")]
        self.codes = []
        self.rows = None

    def login(self):
        return Result("", [])

    def logout(self):
        pass

    def query_trade_dates(self, **params):
        assert params == {"start_date": "2026-11-13", "end_date": "2026-11-16"}
        return Result("calendar_date,is_trading_day", self.calendar)

    def query_history_k_data_plus(self, code, fields, **params):
        self.codes.append(code)
        assert fields == "date,code,close"
        assert params == {"start_date": "2026-11-13", "end_date": "2026-11-16",
                          "frequency": "d", "adjustflag": "3"}
        rows = self.rows if self.rows is not None else [
            ("2026-11-13", code, "3800.00"), ("2026-11-16", code, "3840.00")]
        return Result(fields, rows)


REQUEST = {"start": "2026-11-13", "end": "2026-11-16"}
TODAY = date(2026, 11, 16)


def test_both_indices_share_calendar_and_use_exact_decimal_strings():
    sdk = SDK()
    result = IndexSource(sdk).collect(REQUEST, TODAY)
    assert result["state"] == "AVAILABLE"
    candidate = result["candidate"]
    assert sdk.codes == ["sh.000001", "sh.000300"]
    assert [record["indexId"] for record in candidate["indices"]] == ["SSE_COMPOSITE", "CSI_300"]
    assert candidate["indices"][0]["points"] == [
        {"date": "2026-11-13", "close": "3800"}, {"date": "2026-11-16", "close": "3840"}]
    assert len(candidate["sourcePayloadHash"]) == 64
    assert IndexSource(SDK()).collect(REQUEST, TODAY)["candidate"]["sourcePayloadHash"] == candidate["sourcePayloadHash"]


@pytest.mark.parametrize("rows", [
    [],
    [("2026-11-13", "sh.000001", "3800.00")],
    [("2026-11-13", "sh.000001", "3800.00"), ("2026-11-13", "sh.000001", "3801.00")],
    [("2026-11-13", "sh.000001", "3800.00"), ("2026-11-14", "sh.000001", "3801.00")],
    *[[("2026-11-13", "sh.000001", "3800.00"), ("2026-11-16", code, close)]
      for code, close in [("sh.600030", "3840.00"), ("sh.000001", "NaN"),
                          ("sh.000001", "Infinity"), ("sh.000001", "0"),
                          ("sh.000001", "-100"), ("sh.000001", "1e99")]],
])
def test_rejects_entire_candidate_for_bad_index_rows(rows):
    sdk = SDK()
    sdk.rows = rows
    result = IndexSource(sdk).collect(REQUEST, TODAY)
    assert result["state"] == "SOURCE_INVALID"
    assert result["candidate"] is None


def test_calendar_gap_never_accepts_an_index_snapshot():
    sdk = SDK()
    del sdk.calendar[2]
    result = IndexSource(sdk).collect(REQUEST, TODAY)
    assert result["state"] == "SOURCE_INVALID"
    assert sdk.codes == []


@pytest.mark.parametrize("command", [
    {"start": "2026-11-13", "end": "2026-11-17"},
    {"start": "2026-11-13", "end": "2026-11-16", "indexId": "sh.600030"},
    {"start": "2025-11-13", "end": "2026-11-16"},
])
def test_invalid_request_is_rejected_before_sdk(command):
    assert IndexSource(object()).collect(command, TODAY)["state"] == "SOURCE_INVALID"
