import hashlib
import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.calendar.provider import CalendarSource, validate_request
REQ={'operationId':'calendar-1','start':'2026-10-08','end':'2026-10-12'}
class Result:
    error_code='0';fields=['calendar_date','is_trading_day']
    def __init__(self,rows):self.rows=iter(rows);self.row=None
    def next(self):self.row=next(self.rows,None);return self.row is not None
    def get_row_data(self):return self.row
class SDK:
    def __init__(self):self.rows=[[f'2026-10-{d:02}',str(int(d not in (10,11)))] for d in range(8,13)];self.logouts=0
    def login(self):return Result([])
    def logout(self):self.logouts+=1
    def query_trade_dates(self,**kwargs):return Result(self.rows)
def test_calendar_independent_of_prices_and_has_closed_days():
    sdk=SDK();r=CalendarSource(sdk).collect(REQ);assert r['state']=='AVAILABLE';c=r['candidate'];assert len(c['days'])==5;assert c['days'][2]=={'date':'2026-10-10','open':False};assert c['sourcePayloadHash']==hashlib.sha256(''.join(f'{a}:{b}\n' for a,b in sdk.rows).encode()).hexdigest();assert sdk.logouts==1
@pytest.mark.parametrize('kind',['gap','duplicate','flag','error'])
def test_bad_calendar_is_not_an_empty_success(kind):
    sdk=SDK()
    if kind=='gap':sdk.rows.pop(2)
    if kind=='duplicate':sdk.rows[2]=sdk.rows[1]
    if kind=='flag':sdk.rows[2][1]='true'
    if kind=='error':sdk.login=lambda:type('Failure',(),{'error_code':'10002001'})()
    r=CalendarSource(sdk).collect(REQ);assert r['state'].startswith('SOURCE_');assert r['candidate'] is None;assert sdk.logouts==1
def test_request_rejects_extra_fields_and_excessive_range():
    with pytest.raises(ValueError):validate_request(REQ|{'instrumentId':'SH600030'})
    with pytest.raises(ValueError):validate_request(REQ|{'start':'2020-01-01'})
def test_calendar_http_auth_timeout_busy_and_payload(monkeypatch):
    import subprocess
    from app import main
    monkeypatch.setenv('SERVICE_TOKEN','fixture');client=TestClient(app);url='/internal/v1/market/calendar-collect';h={'X-Service-Token':'fixture'}
    assert client.post(url,json=REQ).status_code==401
    assert client.post(url,json=REQ|{'extra':1},headers=h).status_code==422
    assert client.post(url,content='x'*4097,headers=h).status_code==413
    assert main._COLLECT_SLOT.acquire(blocking=False)
    try:assert client.post(url,json=REQ,headers=h).json()['state']=='SOURCE_BUSY'
    finally:main._COLLECT_SLOT.release()
    def timeout(*a,**k):raise subprocess.TimeoutExpired('worker',25)
    monkeypatch.setattr(subprocess,'run',timeout)
    assert client.post(url,json=REQ,headers=h).json()['state']=='SOURCE_TIMEOUT'

@pytest.mark.parametrize("value", ["20261008", "2026-W42-1", None, 20261008])
def test_request_rejects_noncanonical_dates(value):
    with pytest.raises(ValueError): validate_request(REQ|{"start":value})
