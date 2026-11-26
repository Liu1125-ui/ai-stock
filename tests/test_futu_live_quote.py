"""Isolated tests for the local quote collector, with no OpenD or network calls."""

from datetime import datetime
import importlib.util
import json
from pathlib import Path
import sys
from types import ModuleType, SimpleNamespace

import pytest


@pytest.fixture
def collector(monkeypatch):
    class QuoteHandlerBase:
        def on_recv_rsp(self, response):
            return response

    sdk = ModuleType("futu")
    sdk.OpenQuoteContext = None
    sdk.RET_OK = 0
    sdk.StockQuoteHandlerBase = QuoteHandlerBase
    sdk.SubType = SimpleNamespace(QUOTE="QUOTE")
    monkeypatch.setitem(sys.modules, "futu", sdk)
    file = Path(__file__).resolve().parents[1] / "scripts" / "futu-live-quote.py"
    spec = importlib.util.spec_from_file_location("futu_collector_fixture", file)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def quote(code, timestamp, price):
    return SimpleNamespace(code=code, data_date="2026-11-26", data_time=timestamp, last_price=price)


def push(handler, *rows):
    return handler.on_recv_rsp((0, SimpleNamespace(itertuples=lambda index: iter(rows))))


def test_quote_handler_retains_newest_native_quote(collector):
    handler = collector.QuoteHandler()
    push(handler, quote(collector.CODE, "14:00:03", 420.1))
    push(handler, quote(collector.CODE, "14:00:01", 419.7),
         quote("HK.09988", "14:00:05", 100.0), quote(collector.CODE, "14:00:06", 0.0))
    assert handler.snapshot() == (datetime.fromisoformat("2026-11-26T14:00:03+08:00"), 420.1)


def test_collector_reconnects_after_opend_logout_and_forwards_new_quote(collector, monkeypatch):
    clock = 0
    connections = []
    publications = []

    class QuoteContext:
        def __init__(self, host, port):
            assert (host, port) == ("127.0.0.1", 11111)
            self.session = len(connections) + 1
            self.closed = False
            connections.append(self)

        def set_handler(self, handler):
            self.handler = handler

        def subscribe(self, codes, types, is_first_push):
            assert (codes, types, is_first_push) == ([collector.CODE], ["QUOTE"], True)
            push(self.handler, quote(collector.CODE, f"14:00:0{self.session}", 420 + self.session))
            return 0, None

        def get_global_state(self):
            logged_in = self.session != 1 or clock < 5
            return 0, {"qot_logined": logged_in, "market_hk": "AFTERNOON"}

        def close(self):
            self.closed = True

    def advance(seconds):
        nonlocal clock
        clock += seconds

    def market_sink(token, port, state, price=None):
        assert (token, port) == ("test-quotes", 18082)
        publications.append((clock, state, price[1] if price else None))

    monkeypatch.setattr(collector, "OpenQuoteContext", QuoteContext)
    monkeypatch.setattr(collector, "monotonic", lambda: clock)
    monkeypatch.setattr(collector, "sleep", advance)
    monkeypatch.setattr(collector, "send", market_sink)
    monkeypatch.setenv("AISTOCK_MARKET_LIVE_PILOT_TOKEN", "test-quotes")
    monkeypatch.setattr(sys, "argv", ["futu-live-quote.py", "--seconds", "12"])
    collector.main()

    assert publications == [
        (0, "OPEN", 421.0), (5, "DISCONNECTED", None),
        (10, "OPEN", 422.0), (12, "DISCONNECTED", None),
    ]
    assert len(connections) == 2 and all(connection.closed for connection in connections)


@pytest.mark.parametrize(("status", "error"), [
    (200, None), (403, "ConfigurationError"), (503, "OSError"), (None, "TimeoutError"),
])
def test_market_write_classifies_failure_and_closes_connection(collector, monkeypatch, status, error):
    class Connection:
        def __init__(self, host, port, timeout):
            assert (host, port, timeout) == ("127.0.0.1", 18082, 3)
            self.closed = False

        def request(self, method, path, body, headers):
            assert (method, path) == ("POST", "/internal/v1/realtime/tencent")
            assert headers["X-Quote-Token"] == "test-quotes"
            assert json.loads(body) == {
                "feedState": "OPEN", "currency": "HKD", "price": 420.5,
                "quoteTime": "2026-11-26T14:01:00+08:00",
            }

        def getresponse(self):
            if status is None:
                raise TimeoutError("test HTTP timeout")
            return SimpleNamespace(status=status, read=lambda: b"")

        def close(self):
            self.closed = True

    connection = Connection("127.0.0.1", 18082, 3)
    monkeypatch.setattr(collector, "HTTPConnection", lambda *args, **kwargs: connection)
    timestamp = datetime.fromisoformat("2026-11-26T14:01:00+08:00")
    if error:
        exceptions = {
            "ConfigurationError": collector.ConfigurationError,
            "OSError": OSError,
            "TimeoutError": TimeoutError,
        }
        with pytest.raises(exceptions[error]):
            collector.send("test-quotes", 18082, "OPEN", (timestamp, 420.5))
    else:
        collector.send("test-quotes", 18082, "OPEN", (timestamp, 420.5))
    assert connection.closed
