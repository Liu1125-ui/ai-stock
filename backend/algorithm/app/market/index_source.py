"""Collect a complete, paired window of index closes and the source trading calendar."""

import hashlib
import json
import re
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal

from .providers import BaostockProvider, _SESSION_LOCK

INDEX_CODES = {"SSE_COMPOSITE": "sh.000001", "CSI_300": "sh.000300"}
CALENDAR_FIELDS = "calendar_date,is_trading_day"
INDEX_FIELDS = "date,code,close"
MAX_WINDOW_DAYS = 31
SHANGHAI = timezone(timedelta(hours=8))


def validate_request(command, today=None):
    if not isinstance(command, dict) or set(command) != {"start", "end"}:
        raise ValueError("Invalid index request")
    if any(not isinstance(value, str) or not re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}", value)
           for value in command.values()):
        raise ValueError("Invalid date format")
    start = date.fromisoformat(command["start"])
    end = date.fromisoformat(command["end"])
    today = today or datetime.now(SHANGHAI).date()
    if not date(1991, 1, 1) <= start <= end <= today or (end - start).days >= MAX_WINDOW_DAYS:
        raise ValueError("Invalid index date range")
    return start, end


class SourceError(Exception):
    def __init__(self, source_code):
        self.state = BaostockProvider._sdk_error(source_code)["state"]


class IndexSource:
    def __init__(self, sdk=None):
        if sdk is None:
            import baostock as sdk
        self.sdk = sdk

    def collect(self, command, today=None):
        try:
            start, end = validate_request(command, today)
        except ValueError:
            return self.error("SOURCE_INVALID")

        date_args = {"start_date": start.isoformat(), "end_date": end.isoformat()}
        with _SESSION_LOCK:
            try:
                self._check(self.sdk.login())
                calendar = self._calendar(start, end, date_args)
                trading_days = [day["date"] for day in calendar if day["open"]]
                if len(trading_days) < 2:
                    raise ValueError("Two closes are needed to calculate a change")
                indices = [self._index(index_id, code, trading_days, date_args)
                           for index_id, code in INDEX_CODES.items()]
                evidence = {"calendar": calendar, "indices": indices}
                serialized = json.dumps(evidence, sort_keys=True, separators=(",", ":"))
                return {
                    "state": "AVAILABLE",
                    "errorCode": None,
                    "candidate": {
                        "provider": "baostock", "start": date_args["start_date"], "end": date_args["end_date"],
                        "fetchedAt": datetime.now(timezone.utc).isoformat(),
                        "sourcePayloadHash": hashlib.sha256(serialized.encode()).hexdigest(),
                        **evidence,
                    },
                }
            except SourceError as exc:
                return self.error(exc.state)
            except TimeoutError:
                return self.error("SOURCE_TIMEOUT")
            except OSError:
                return self.error("SOURCE_UNAVAILABLE")
            except (ValueError, TypeError):
                return self.error("SOURCE_INVALID")
            finally:
                try:
                    self.sdk.logout()
                except Exception:
                    pass

    def _calendar(self, start, end, date_args):
        count = (end - start).days + 1
        rows = self._rows(self.sdk.query_trade_dates(**date_args), CALENDAR_FIELDS, count)
        if len(rows) != count:
            raise ValueError("Missing calendar dates")
        calendar = []
        for offset, (day, open_flag) in enumerate(rows):
            if day != (start + timedelta(days=offset)).isoformat() or open_flag not in ("0", "1"):
                raise ValueError("Invalid calendar row")
            calendar.append({"date": day, "open": open_flag == "1"})
        return calendar

    def _index(self, index_id, source_code, trading_days, date_args):
        result = self.sdk.query_history_k_data_plus(
            source_code, INDEX_FIELDS, **date_args, frequency="d", adjustflag="3")
        rows = self._rows(result, INDEX_FIELDS, len(trading_days))
        if len(rows) != len(trading_days):
            raise ValueError("Missing index trading days")
        points = []
        for (day, received_code, close), expected in zip(rows, trading_days, strict=True):
            if day != expected or received_code != source_code:
                raise ValueError("Unexpected index identity or trading date")
            if not isinstance(close, str) or not re.fullmatch(r"[0-9]{1,10}(\.[0-9]{1,8})?", close):
                raise ValueError("Invalid index close format")
            value = Decimal(close)
            if value <= 0:
                raise ValueError("Index close must be positive")
            points.append({"date": day, "close": format(value.normalize(), "f")})
        return {"indexId": index_id, "sourceCode": source_code, "points": points}

    @classmethod
    def _rows(cls, result, fields, limit):
        cls._check(result)
        expected_fields = fields.split(",")
        if list(result.fields) != expected_fields:
            raise ValueError("Unexpected source fields")
        rows = []
        while result.next():
            row = result.get_row_data()
            if len(row) != len(expected_fields) or len(rows) == limit:
                raise ValueError("Unexpected source row")
            rows.append(row)
        cls._check(result)
        return rows

    @staticmethod
    def _check(result):
        if str(result.error_code) != "0":
            raise SourceError(result.error_code)

    @staticmethod
    def error(state):
        return {"state": state, "candidate": None, "errorCode": state}
