"""Collect a pinned, calendar-aligned Baostock training increment."""
import argparse
import csv
from datetime import datetime, time, timedelta, timezone
from decimal import localcontext
import hashlib
import io
import json
from pathlib import Path
import socket
import tempfile

from app.forecast.provider import FIELDS as SOURCE_FIELDS, ForecastSource, SourceFailure
from app.inference.artifacts import SOURCE
from app.inference.rolling_artifacts import unique_fields
from app.market.providers import _SESSION_LOCK
from .increment import merge, number, source_hashes
from .seal import canonical
from .snapshot import FIELDS, RAW_LIMIT, archive_rows, checked_read, parse_date


def trading_sessions(source, start, through):
    rows = source.read(source.sdk.query_trade_dates(start_date=str(start), end_date=str(through)),
                       "calendar_date,is_trading_day")
    sessions = []
    expected = start
    for day, flag in rows:
        if day != str(expected) or expected > through or flag not in ("0", "1"):
            raise ValueError("Incomplete Baostock calendar")
        if flag == "1":
            sessions.append(expected)
        expected += timedelta(days=1)
    if expected != through + timedelta(days=1) or not sessions:
        raise ValueError("Incomplete Baostock calendar")
    return sessions


def stock_increment(source, stock, start, as_of, sessions):
    code = stock[:2].lower() + "." + stock[2:]
    pairs = []
    for flag in ("3", "1"):
        result = source.sdk.query_history_k_data_plus(code, SOURCE_FIELDS, start_date=str(start),
                                                      end_date=str(as_of), frequency="d", adjustflag=flag)
        rows = source.read(result, SOURCE_FIELDS)
        previous = None
        for row in rows:
            day = parse_date(row[0])
            if (row[1] != code or row[8] != flag or row[9] not in ("0", "1")
                    or day not in sessions or previous is not None and day <= previous):
                raise ValueError(f"{stock}: Invalid Baostock row identity or order")
            previous = day
        pairs.append(rows)
    raw, adjusted = pairs
    if [(r[0], r[9]) for r in raw] != [(r[0], r[9]) for r in adjusted]:
        raise ValueError(f"{stock}: Unpaired Baostock rows")
    if [row[0] for row in raw] != [str(day) for day in sessions]:
        return None

    output = io.StringIO(newline="")
    writer = csv.writer(output, lineterminator="\n")
    writer.writerow(FIELDS)
    with localcontext() as context:
        context.prec = 34
        for row, other in zip(raw, adjusted):
            raw_close, adjusted_close = number(row[5]), number(other[5])
            factor = None
            if raw_close is not None and raw_close > 0 and adjusted_close is not None and adjusted_close > 0:
                factor = format(adjusted_close / raw_close, "f")
            if row[9] == "1" and factor is None:
                raise ValueError(f"{stock}: Missing valid adjustment")
            writer.writerow((row[0], *row[2:8], factor, row[9]))
    return output.getvalue().encode("utf-8")


def collect(base, index, index_hash, start, through, output, *, sdk=None, already_published=None):
    now = datetime.now(timezone(timedelta(hours=8)))
    completed = now.date() if now.time() >= time(20) else now.date() - timedelta(days=1)
    if not start <= through <= completed or (through - start).days > 120:
        raise ValueError("Collection requires a completed date range of at most 121 days")
    base = base.resolve(strict=True)
    output = output.resolve()
    if output.exists() or output.is_relative_to(base):
        raise ValueError("Output must be a new directory outside the base archive")
    previous = json.loads(checked_read(index, 65536, index_hash), object_pairs_hook=unique_fields)
    if not previous["source"].startswith("Baostock,"):
        raise ValueError("Incompatible base archive provider")
    archived = source_hashes(previous)
    old = {stock: checked_read(base / f"{stock}_canonical.csv", RAW_LIMIT, digest)
           for stock, digest in archived.items()}
    source = ForecastSource(sdk)
    features, missing = [], {}
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".training-collection-", dir=output.parent) as temp:
        staged = Path(temp) / "delta"
        staged.mkdir()
        with _SESSION_LOCK:
            try:
                source.check(source.sdk.login())
                calendar = trading_sessions(source, start, through)
                as_of = calendar[-1]
                if already_published is not None and as_of <= already_published:
                    if as_of != already_published:
                        raise ValueError("Latest source session precedes the published snapshot")
                    return {"unchanged": True, "asOf": str(as_of),
                            "calendar": {"verifiedThrough": str(through),
                                         "sessions": [str(day) for day in calendar]}}
                for stock, raw in sorted(old.items()):
                    last = next(reversed(archive_rows(raw, as_of)))
                    overlap = [day for day in calendar if day <= last]
                    if len(overlap) < 3 or last not in calendar or last == as_of:
                        missing[stock] = "ARCHIVE_BOUNDARY_OUTSIDE_INCREMENT"
                        continue
                    data = stock_increment(source, stock, start, as_of, calendar)
                    if data is None:
                        missing[stock] = "SOURCE_DATES_INCOMPLETE"
                        continue
                    try:
                        merge(raw, data, calendar, as_of)
                    except ValueError as error:
                        raise ValueError(f"{stock}: {error}") from None
                    (staged / f"{stock}_canonical.csv").write_bytes(data)
                    features.append({"stock": stock, "raw_sha256": hashlib.sha256(data).hexdigest()})
            except SourceFailure as error:
                raise ValueError(f"Baostock: {error.code}") from None
            finally:
                try:
                    source.sdk.logout()
                except Exception:
                    pass
        if not features:
            raise ValueError("No verified new trading rows")
        calendar_bytes = canonical({"verifiedThrough": str(through), "sessions": [str(day) for day in calendar]})
        calendar_hash = hashlib.sha256(calendar_bytes).hexdigest()
        metadata = canonical({"provider": "baostock", "basis": SOURCE, "asOf": str(as_of),
                              "baseIndexHash": index_hash, "calendarHash": calendar_hash,
                              "features": features, "omittedStocks": missing})
        (staged / "calendar.json").write_bytes(calendar_bytes)
        (staged / "index.json").write_bytes(metadata)
        staged.rename(output)
    return {"asOf": str(as_of), "indexHash": hashlib.sha256(metadata).hexdigest(),
            "calendarHash": calendar_hash, "stocks": len(features), "omittedStocks": missing}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for key in ("base", "index", "output"):
        parser.add_argument("--" + key, type=Path, required=True)
    parser.add_argument("--index-sha256", required=True)
    for key in ("start", "through"):
        parser.add_argument("--" + key, type=parse_date, required=True)
    args = parser.parse_args()
    socket.setdefaulttimeout(20)
    try:
        receipt = collect(args.base, args.index, args.index_sha256, args.start, args.through, args.output)
    except (ValueError, OSError, KeyError, TypeError) as error:
        parser.error(str(error))
    print(canonical(receipt).decode())


if __name__ == "__main__":
    main()
