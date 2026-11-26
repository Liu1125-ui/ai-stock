"""Comparison source fixtures; no internet requests."""
import pytest
from fastapi.testclient import TestClient
from app.main import app
from test_forecast_source import SDK, REQUEST
from app.comparison.provider import ComparisonSource, validate_request

REQ={k:v for k,v in REQUEST.items() if k!="calendarThrough"}

def test_pairs_historical_source_without_forecast_horizon():
    sdk=SDK(); result=ComparisonSource(sdk).collect(REQ)
    assert sdk.flags==["3","1"]
    assert result["state"]=="AVAILABLE"
    c=result["candidate"]
    assert c["definition"]=="baostock-comparison-backward-close-v1"
    assert c["from"]==REQ["start"] and c["to"]==REQ["end"]
    assert c["points"][0]["rawClose"]=="10"
    assert c["points"][0]["backwardClose"]=="20"
    assert c["points"][0]["volumeShares"]=="100"
    assert len(c["calendar"])==2

@pytest.mark.parametrize("change",[
    {"start":"20260929"},{"end":"2099-01-01"},{"start":"2025-09-29"},
    {"instrumentId":"sh.600030"},{"extra":1},{"operationId":1},
])
def test_rejects_bad_requests_without_source(change):
    sdk=SDK()
    assert ComparisonSource(sdk).collect(REQ|change)["state"]=="SOURCE_INVALID"
    assert sdk.flags==[]

def test_pair_mismatch_and_source_timeout_are_not_success():
    sdk=SDK();sdk.mismatch=True
    assert ComparisonSource(sdk).collect(REQ)["candidate"] is None
    sdk=SDK();sdk.error="10002008"
    assert ComparisonSource(sdk).collect(REQ)["state"]=="SOURCE_TIMEOUT"

def test_natural_year_range():
    validate_request(REQ|{"start":"2023-02-28","end":"2024-02-29"})
    with pytest.raises(ValueError):
        validate_request(REQ|{"start":"2023-02-27","end":"2024-02-29"})

def test_http_auth_limit_unknown_fields_busy_and_timeout(monkeypatch):
    import subprocess
    from app import main
    monkeypatch.setenv("SERVICE_TOKEN","fixture")
    client=TestClient(app); url="/internal/v1/market/comparison-collect"; h={"X-Service-Token":"fixture"}
    assert client.post(url,content="{").status_code==401
    assert client.post(url,content="x"*4097,headers=h).status_code==413
    assert client.post(url,json=REQ|{"extra":1},headers=h).status_code==422
    assert main._COLLECT_SLOT.acquire(blocking=False)
    try:
        assert client.post(url,json=REQ,headers=h).json()["state"]=="SOURCE_BUSY"
    finally:
        main._COLLECT_SLOT.release()
    def timeout(*a,**k):raise subprocess.TimeoutExpired("worker",25)
    monkeypatch.setattr("app.comparison.process.run_bounded_worker",timeout)
    assert client.post(url,json=REQ,headers=h).json()["state"]=="SOURCE_TIMEOUT"

def test_oversized_worker_output_is_rejected(monkeypatch):
    import subprocess
    monkeypatch.setenv("SERVICE_TOKEN","fixture")
    def oversized(*a,**k):raise ValueError("Worker output limit exceeded")
    monkeypatch.setattr("app.comparison.process.run_bounded_worker",oversized)
    response=TestClient(app).post("/internal/v1/market/comparison-collect",json=REQ,headers={"X-Service-Token":"fixture"})
    assert response.json()["state"]=="SOURCE_INVALID"

def test_oversized_sdk_rows_are_rejected_before_candidate():
    class HugeSDK(SDK):
        def query_history_k_data_plus(self,*args,**kwargs):
            from test_forecast_source import Result, FIELDS
            flag=kwargs["adjustflag"]
            return Result(FIELDS,[["2026-09-29","sh.600030","10","11","9","10","100","1000",flag,"1"]]*371)
    assert ComparisonSource(HugeSDK()).collect(REQ)["state"]=="SOURCE_INVALID"

def test_wrong_source_code_and_empty_rows_are_rejected():
    class WrongSDK(SDK):
        def query_history_k_data_plus(self,*args,**kwargs):
            r=super().query_history_k_data_plus(*args,**kwargs)
            r.rows[0][1]="sh.600276"
            return r
    class EmptySDK(SDK):
        def query_history_k_data_plus(self,*args,**kwargs):
            r=super().query_history_k_data_plus(*args,**kwargs)
            r.rows=[]
            return r
    for sdk in (WrongSDK(),EmptySDK()):
        assert ComparisonSource(sdk).collect(REQ)["candidate"] is None

def test_raw_and_backward_trade_status_must_match():
    class MismatchStatusSDK(SDK):
        def query_history_k_data_plus(self,*args,**kwargs):
            result=super().query_history_k_data_plus(*args,**kwargs)
            if kwargs["adjustflag"]=="1":
                result.rows[0][9]="0"
            return result
    assert ComparisonSource(MismatchStatusSDK()).collect(REQ)["state"]=="SOURCE_INVALID"
