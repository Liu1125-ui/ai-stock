"""Forward the HK.00700 quote subscription from local OpenD to aiStock."""

import argparse
from datetime import datetime, timedelta, timezone
from http.client import HTTPConnection
import json
import os
from threading import Lock
from time import monotonic, sleep

from futu import OpenQuoteContext, RET_OK, StockQuoteHandlerBase, SubType

CODE = "HK.00700"
HK_TIME = timezone(timedelta(hours=8))
OPEN_PHASES = {"MORNING", "AFTERNOON", "AUCTION", "HK_CAS"}
CLOSED_PHASES = {"NONE", "CLOSED", "REST", "WAITING_OPEN", "PRE_MARKET_BEGIN",
                 "PRE_MARKET_END", "AFTER_HOURS_BEGIN", "AFTER_HOURS_END"}


class ConfigurationError(Exception):
    pass


class QuoteHandler(StockQuoteHandlerBase):
    def __init__(self):
        super().__init__()
        self.lock = Lock()
        self.latest = None

    def on_recv_rsp(self, response):
        status, rows = super().on_recv_rsp(response)
        if status == RET_OK:
            with self.lock:
                for row in rows.itertuples(index=False):
                    if row.code != CODE or not row.last_price > 0:
                        continue
                    timestamp = datetime.fromisoformat(
                        f"{row.data_date}T{row.data_time}"
                    ).replace(tzinfo=HK_TIME)
                    if self.latest is None or timestamp >= self.latest[0]:
                        self.latest = (timestamp, float(row.last_price))
        return status, rows

    def snapshot(self):
        with self.lock:
            return self.latest


def send(token, port, state, quote=None):
    payload = {"feedState": state}
    if quote:
        timestamp, price = quote
        payload.update(currency="HKD", price=price, quoteTime=timestamp.isoformat())
    connection = HTTPConnection("127.0.0.1", port, timeout=3)
    try:
        connection.request("POST", "/internal/v1/realtime/tencent", json.dumps(payload),
                           {"Content-Type": "application/json", "X-Quote-Token": token})
        response = connection.getresponse()
        response.read()
        if response.status in (401, 403):
            raise ConfigurationError("Market service rejected the live quote token")
        if response.status != 200:
            raise OSError(f"Market service returned HTTP {response.status}")
    finally:
        connection.close()


def forward_session(token, port, deadline):
    context = OpenQuoteContext(host="127.0.0.1", port=11111)
    try:
        handler = QuoteHandler()
        context.set_handler(handler)
        status, result = context.subscribe([CODE], [SubType.QUOTE], is_first_push=True)
        if status != RET_OK:
            raise OSError(f"OpenD subscription failed: {result}")
        state = "WAITING"
        last_market_check = last_sent = float("-inf")
        last_quote = None
        while monotonic() < deadline:
            now = monotonic()
            if now - last_market_check >= 5:
                status, info = context.get_global_state()
                if status != RET_OK or not info.get("qot_logined"):
                    raise OSError("OpenD quote connection is unavailable")
                phase = info.get("market_hk")
                state = "OPEN" if phase in OPEN_PHASES else (
                    "CLOSED" if phase in CLOSED_PHASES else "WAITING"
                )
                last_market_check = now
            quote = handler.snapshot()
            if quote != last_quote or now - last_sent >= 5:
                send(token, port, state, quote)
                if state != "OPEN" or quote != last_quote:
                    print(f"{CODE}: {state}" + (f" at {quote[0].isoformat()}" if quote else ""), flush=True)
                last_quote, last_sent = quote, now
            sleep(1)
    finally:
        context.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seconds", type=int, help="Optional duration for a bounded smoke test")
    parser.add_argument("--market-port", type=int, default=18082)
    args = parser.parse_args()
    if not 1 <= args.market_port <= 65535:
        parser.error("--market-port must be between 1 and 65535")
    if args.seconds is not None and args.seconds <= 0:
        parser.error("--seconds must be positive")
    token = os.getenv("AISTOCK_MARKET_LIVE_PILOT_TOKEN", "")
    if not token.strip():
        parser.error("AISTOCK_MARKET_LIVE_PILOT_TOKEN is required")
    deadline = monotonic() + args.seconds if args.seconds else float("inf")
    try:
        while monotonic() < deadline:
            try:
                forward_session(token, args.market_port, deadline)
            except (OSError, TimeoutError) as error:
                print(f"{error}; retrying OpenD connection", flush=True)
                try:
                    send(token, args.market_port, "DISCONNECTED")
                except (OSError, TimeoutError):
                    pass
                sleep(min(5, max(0, deadline - monotonic())))
    except KeyboardInterrupt:
        pass
    except ConfigurationError as error:
        parser.exit(2, f"{error}\n")
    finally:
        try:
            send(token, args.market_port, "DISCONNECTED")
        except (ConfigurationError, OSError, TimeoutError):
            pass


if __name__ == "__main__":
    main()
