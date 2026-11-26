"""Hand-authored fixtures: source boundary only, not observed market data."""
from datetime import date
import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.alert.provider import AlertSource, validate_request
from test_forecast_source import SDK, REQUEST
REQ=REQUEST|{'calendarThrough':REQUEST['end']}
def test_alert_only_collects_historical_calendar_and_keeps_pair_units():
    class HistoricalSDK(SDK):
        def query_trade_dates(self,start_date,end_date):
            assert end_date=='2026-09-30'
            return super().query_trade_dates(start_date,end_date)
    sdk=HistoricalSDK();result=AlertSource(sdk).collect(REQ)
    assert result['state']=='AVAILABLE' and len(result['candidate']['calendar'])==2
    assert result['candidate']['bars'][0]['raw']['close']=='10'
    assert result['candidate']['bars'][0]['backwardClose']=='20'
    assert sdk.flags==['3','1'] and sdk.logouts==1
@pytest.mark.parametrize('change',[{'calendarThrough':'2026-10-05'},{'start':'20260929'},{'end':'2026-W40-3'},{'end':'2099-01-01','calendarThrough':'2099-01-01'}])
def test_no_future_or_noncanonical_alert_dates(change):
    with pytest.raises(ValueError):validate_request(REQ|change)
def test_alert_route_auth_bound_and_shared_slot(monkeypatch):
    from app import main
    monkeypatch.setenv('SERVICE_TOKEN','fixture');client=TestClient(app);url='/internal/v1/market/alert-collect';h={'X-Service-Token':'fixture'}
    assert client.post(url,content='{').status_code==401
    assert client.post(url,content='x'*4097,headers=h).status_code==413
    assert client.post(url,json=REQ|{'calendarThrough':'2026-10-05'},headers=h).status_code==422
    assert main._COLLECT_SLOT.acquire(blocking=False)
    try:assert client.post(url,json=REQ,headers=h).json()['state']=='SOURCE_BUSY'
    finally:main._COLLECT_SLOT.release()
    def timeout(*a,**k):
        import subprocess
        raise subprocess.TimeoutExpired('worker',25)
    monkeypatch.setattr('subprocess.run',timeout)
    assert client.post(url,json=REQ,headers=h).json()['state']=='SOURCE_TIMEOUT'
