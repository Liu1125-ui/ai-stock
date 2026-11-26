"""Sync the searchable mainland stock directory without creating market data."""

import argparse
from datetime import datetime, timezone
import re
import subprocess

SOURCE_CODE = re.compile(r"(?:sh\.(?:60|68)|sz\.(?:00|30))[0-9]{4}")
INSTRUMENT_ID = re.compile(r"(?:SH(?:60|68)|SZ(?:00|30))[0-9]{4}")
DEFAULT_CONTAINER = "aistock-sdd001-mysql-1"


def directory_rows(source):
    instruments = {}
    for code, name, security_type, status in source:
        if security_type != "1" or status != "1" or not SOURCE_CODE.fullmatch(code):
            continue
        name = name.strip()
        if not name or len(name) > 128 or any(ord(character) < 32 for character in name):
            raise ValueError(f"Invalid stock name for {code}")
        instrument_id = code.replace(".", "").upper()
        if instruments.setdefault(instrument_id, name) != name:
            raise ValueError(f"Conflicting stock names for {code}")
    if not instruments:
        raise ValueError("No eligible A-share instruments in source directory")
    return dict(sorted(instruments.items()))


def fetch_directory():
    import baostock

    session = baostock.login()
    if session.error_code != "0":
        raise RuntimeError(f"BaoStock login failed: {session.error_code}")
    try:
        result = baostock.query_stock_basic()
        fields = ("code", "code_name", "type", "status")
        if result.error_code != "0" or not set(fields).issubset(result.fields):
            raise RuntimeError(f"BaoStock directory query failed: {result.error_code}")
        positions = [result.fields.index(field) for field in fields]
        rows = []
        while result.next():
            row = result.get_row_data()
            rows.append(tuple(row[position] for position in positions))
        if result.error_code != "0":
            raise RuntimeError(f"BaoStock directory read failed: {result.error_code}")
        return directory_rows(rows)
    finally:
        baostock.logout()


def import_sql(instruments, checked_at):
    timestamp = checked_at.astimezone(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
    values = []
    for instrument_id, name in instruments.items():
        if not INSTRUMENT_ID.fullmatch(instrument_id):
            raise ValueError(f"Invalid instrument ID: {instrument_id}")
        name_hex = name.encode("utf-8").hex()
        values.append(
            f"('{instrument_id}','{instrument_id}',CONVERT(0x{name_hex} USING utf8mb4),"
            f"false,'ACTIVE',true,'baostock','{timestamp}')"
        )
    statements = ["START TRANSACTION;"]
    for start in range(0, len(values), 500):
        batch = ",\n".join(values[start:start + 500])
        statements.append(
            "INSERT INTO instrument(instrument_id,ticker,display_name,demo_only,"
            "listing_status,name_verified,metadata_source,metadata_checked_at) VALUES\n"
            + batch + "\nON DUPLICATE KEY UPDATE instrument_id=instrument.instrument_id;"
        )
    statements.append("COMMIT;\n")
    return "\n".join(statements)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="write to the local database")
    parser.add_argument("--container", default=DEFAULT_CONTAINER, help="local MySQL container")
    args = parser.parse_args()
    instruments = fetch_directory()
    print(f"Eligible listed A-share instruments: {len(instruments)}", flush=True)
    if args.apply:
        command = 'MYSQL_PWD="$MARKET_DB_PASSWORD" mysql --batch -u stock_market stock_market'
        subprocess.run(
            ["docker", "exec", "-i", "--", args.container, "sh", "-c", command],
            input=import_sql(instruments, datetime.now(timezone.utc)),
            encoding="ascii", check=True, timeout=60,
        )
        print("Stock directory sync committed.")


if __name__ == "__main__":
    main()
