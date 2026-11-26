"""Bounded historical comparison evidence, never a price prediction."""
from datetime import date, datetime, timedelta, timezone
import re
from uuid import UUID
from app.forecast.provider import ForecastSource

DEFINITION = "baostock-comparison-backward-close-v1"
KEYS = {"operationId", "instrumentId", "rawVersionId", "start", "end"}

def validate_request(req):
    if not isinstance(req, dict) or set(req) != KEYS:
        raise ValueError("Invalid fields")
    for key, pattern in (("operationId", r"[A-Za-z0-9_-]{1,128}"),
                         ("instrumentId", r"(SH|SZ)[0-9]{6}"),
                         ("rawVersionId", r"[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}")):
        if not isinstance(req[key], str) or not re.fullmatch(pattern, req[key]):
            raise ValueError("Invalid identity")
    UUID(req["rawVersionId"])
    for key in ("start", "end"):
        if not isinstance(req[key], str) or not re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}", req[key]):
            raise ValueError("Invalid date")
    start, end = (date.fromisoformat(req[k]) for k in ("start", "end"))
    try:
        floor = end.replace(year=end.year-1)
    except ValueError:
        floor = end.replace(year=end.year-1, day=28)
    today = datetime.now(timezone(timedelta(hours=8))).date()
    if not date(1990, 1, 1) <= start <= end <= today or start < floor:
        raise ValueError("Invalid historical range")
    return start, end, end

class ComparisonSource(ForecastSource):
    def parse_request(self, req):
        if req.get("calendarThrough") != req.get("end"):
            raise ValueError("Invalid calendar boundary")
        return validate_request({k:v for k,v in req.items() if k != "calendarThrough"})

    def read(self, result, fields):
        self.check(result)
        if list(result.fields) != fields.split(","):
            raise ValueError("Source fields mismatch")
        rows = []
        limit = 367 if fields == "calendar_date,is_trading_day" else 370
        while result.next():
            row = result.get_row_data()
            if len(row) != len(result.fields) or any(not isinstance(v,str) or len(v)>256 for v in row):
                raise ValueError("Invalid source row")
            rows.append(row)
            if len(rows) > limit:
                raise ValueError("Source too large")
        self.check(result)
        if fields != "calendar_date,is_trading_day" and rows:
            if rows[0][8] == "3":
                self._raw_status = {r[0]:r[9] for r in rows}
            elif rows[0][8] == "1":
                if any(self._raw_status.get(r[0]) != r[9] for r in rows):
                    raise ValueError("Paired trade status mismatch")
        return rows

    def collect(self, req):
        try:
            validate_request(req)
        except (ValueError, TypeError, KeyError, AttributeError):
            return self.error("SOURCE_INVALID")
        self._raw_status = {}
        result = super().collect(req | {"calendarThrough": req["end"]})
        if result["state"] != "AVAILABLE":
            return result
        old = result["candidate"]
        points = [{"date": p["raw"]["date"], "rawClose": p["raw"]["close"],
                   "backwardClose": p["backwardClose"],
                   "volumeShares": p["raw"]["volumeShares"],
                   "tradeStatus": p["raw"]["tradeStatus"]} for p in old["bars"]]
        result["candidate"] = {k:old[k] for k in (
            "operationId","instrumentId","rawVersionId","provider","fetchedAt","sourcePayloadHash","calendar")}
        result["candidate"].update(definition=DEFINITION, **{"from":req["start"],"to":req["end"]}, points=points)
        return result