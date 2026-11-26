import json
import subprocess

from fastapi.testclient import TestClient
from app import main

PATH = "/internal/v1/market/index-collect"
HEADERS = {"X-Service-Token": "test-token"}
BODY = {"start": "2020-01-06", "end": "2020-01-10"}


def test_index_endpoint_rejects_unauthorized_and_oversize_requests(monkeypatch):
    monkeypatch.setenv("SERVICE_TOKEN", "test-token")
    with TestClient(main.app) as client:
        assert client.post(PATH, json=BODY).status_code == 401
        assert client.post(PATH, json=BODY | {"id": "sh.600000"}, headers=HEADERS).status_code == 422
        assert client.post(PATH, content=" " * 4097, headers=HEADERS).status_code == 413


def test_index_endpoint_uses_bounded_worker(monkeypatch):
    monkeypatch.setenv("SERVICE_TOKEN", "test-token")

    def timeout(args, **kwargs):
        assert args[-1] == "app.market.index_worker"
        assert kwargs["timeout"] == 25
        assert json.loads(kwargs["input"]) == BODY
        raise subprocess.TimeoutExpired(args, kwargs["timeout"])

    monkeypatch.setattr(subprocess, "run", timeout)
    with TestClient(main.app) as client:
        result = client.post(PATH, json=BODY, headers=HEADERS)
    assert result.json() == {"state": "SOURCE_TIMEOUT", "candidate": None, "errorCode": "SOURCE_TIMEOUT"}


def test_index_endpoint_shares_existing_collection_capacity(monkeypatch):
    monkeypatch.setenv("SERVICE_TOKEN", "test-token")
    assert main._COLLECT_SLOT.acquire(blocking=False)
    try:
        with TestClient(main.app) as client:
            result = client.post(PATH, json=BODY, headers=HEADERS)
        assert result.json()["state"] == "SOURCE_BUSY"
    finally:
        main._COLLECT_SLOT.release()
