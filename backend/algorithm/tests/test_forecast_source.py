from datetime import date, timedelta
import importlib
import subprocess
import pytest
from fastapi.testclient import TestClient
from app.main import app

REQUEST={'operationId':'forecast-1','instrumentId':'SH600030','rawVersionId':'11111111-1111-1111-1111-111111111111','start':'2026-09-29','end':'2026-09-30','calendarThrough':'2026-10-16'}
FIELDS='date,code,open,high,low,close,volume,amount,adjustflag,tradestatus'
class Result:
    def __init__(self,fields,rows,code='0'):
        self.fields=fields.split(',');self.rows=rows;self.error_code=code;self.at=-1
    def next(self):self.at+=1;return self.at<len(self.rows)
    def get_row_data(self):return self.rows[self.at]
class SDK:
    def __init__(self):self.flags=[];self.mismatch=False;self.calendar_gap=False;self.error='0';self.logouts=0
    def login(self):return Result('',[])
    def logout(self):self.logouts+=1
    def query_history_k_data_plus(self,code,fields,**kw):
        assert code=='sh.600030' and fields==FIELDS and kw['frequency']=='d'
        flag=kw['adjustflag'];self.flags.append(flag)
        days=['2026-09-29','2026-09-30']
        if self.mismatch and flag=='1':days=days[:1]
        close='20' if flag=='1' else '10'
        return Result(FIELDS,[[d,code,'10','11','9',close,'100','1000',flag,'1'] for d in days],self.error)
    def query_trade_dates(self,start_date,end_date):
        d=date.fromisoformat(start_date);end=date.fromisoformat(end_date);rows=[]
        while d<=end:
            opened=d.weekday()<5 and not date(2026,10,1)<=d<=date(2026,10,7)
            rows.append([d.isoformat(),'1' if opened else '0']);d+=timedelta(days=1)
        if self.calendar_gap:rows.pop(2)
        return Result('calendar_date,is_trading_day',rows)
def provider(sdk):
    return importlib.import_module('app.forecast.provider').ForecastSource(sdk)

def test_new_route_authenticates_before_parsing_malformed_json(monkeypatch):
    monkeypatch.setenv('SERVICE_TOKEN','private-test')
    r=TestClient(app).post('/internal/v1/market/forecast-collect',content='{')
    assert r.status_code==401

def test_real_boundary_pairs_adjustment_without_changing_raw_units():
    sdk=SDK();r=provider(sdk).collect(REQUEST)
    assert r['state']=='AVAILABLE' and sdk.flags==['3','1'] and sdk.logouts==1
    b=r['candidate']['bars'][0]
    assert b['raw']['close']=='10' and b['raw']['volumeShares']=='100' and b['raw']['amountYuan']=='1000'
    assert b['backwardClose']=='20' and b['raw']['sourceAdjustmentRatio'] is None
    assert r['candidate']['definition']=='baostock-backward-close-over-raw-v1'
    targets=[x['date'] for x in r['candidate']['calendar'] if x['date']>'2026-09-30' and x['open']][:5]
    assert targets==['2026-10-08','2026-10-09','2026-10-12','2026-10-13','2026-10-14']

def test_pair_mismatch_and_calendar_gap_never_produce_candidate():
    sdk=SDK();sdk.mismatch=True
    r=provider(sdk).collect(REQUEST);assert r['state']=='SOURCE_INVALID' and r['candidate'] is None
    sdk=SDK();sdk.calendar_gap=True
    r=provider(sdk).collect(REQUEST);assert r['state']=='SOURCE_INVALID' and r['candidate'] is None

def test_transport_error_is_explicit_and_preserves_no_success():
    sdk=SDK();sdk.error='10002008'
    r=provider(sdk).collect(REQUEST);assert r['state']=='SOURCE_TIMEOUT' and r['candidate'] is None

@pytest.mark.parametrize(('column', 'bad_value'), [
    (0, '2026-09-30'),
    (1, 'sh.600519'),
    (5, 'NaN'),
    (5, '0'),
    (6, '-1'),
    (8, '2'),
    (9, '2'),
])
def test_invalid_daily_bar_cannot_become_forecast_input(monkeypatch, column, bad_value):
    sdk = SDK()
    query = sdk.query_history_k_data_plus

    def corrupt(*args, **kwargs):
        result = query(*args, **kwargs)
        result.rows[0][column] = bad_value
        return result

    monkeypatch.setattr(sdk, 'query_history_k_data_plus', corrupt)
    assert provider(sdk).collect(REQUEST) == {
        'state': 'SOURCE_INVALID', 'candidate': None, 'errorCode': 'SOURCE_INVALID',
    }
    assert sdk.flags == ['3']
    assert sdk.logouts == 1


@pytest.mark.parametrize(('error', 'status'), [
    (TimeoutError, 'SOURCE_TIMEOUT'),
    (OSError, 'SOURCE_UNAVAILABLE'),
])
def test_failed_source_connection_keeps_primary_error_when_logout_fails(monkeypatch, error, status):
    sdk = SDK()

    def unavailable():
        raise error('test connection failure')

    def broken_logout():
        sdk.logouts += 1
        raise OSError('test logout failure')

    monkeypatch.setattr(sdk, 'login', unavailable)
    monkeypatch.setattr(sdk, 'logout', broken_logout)
    assert provider(sdk).collect(REQUEST) == {
        'state': status, 'candidate': None, 'errorCode': status,
    }
    assert sdk.flags == []
    assert sdk.logouts == 1


def test_bad_range_and_future_prices_rejected_without_sdk_call():
    sdk=SDK();r=provider(sdk).collect({**REQUEST,'end':'2099-01-01'})
    assert r['state']=='SOURCE_INVALID' and sdk.flags==[]

def test_authenticated_malformed_and_large_body_are_controlled(monkeypatch):
    monkeypatch.setenv('SERVICE_TOKEN','private-test');client=TestClient(app);h={'X-Service-Token':'private-test'}
    assert client.post('/internal/v1/market/forecast-collect',content='{',headers=h).status_code==422
    assert client.post('/internal/v1/market/forecast-collect',content=b'x'*(2097152+1),headers=h).status_code==413

def test_source_timeout_and_busy_are_not_success(monkeypatch):
    from app import main
    monkeypatch.setenv('SERVICE_TOKEN','private-test')
    def timeout(*a,**k):raise subprocess.TimeoutExpired('source',25)
    monkeypatch.setattr(subprocess,'run',timeout)
    client=TestClient(app);h={'X-Service-Token':'private-test'}
    r=client.post('/internal/v1/market/forecast-collect',json=REQUEST,headers=h)
    assert r.status_code==200 and r.json()['state']=='SOURCE_TIMEOUT' and r.json()['candidate'] is None
    assert main._COLLECT_SLOT.acquire(blocking=False)
    try:assert client.post('/internal/v1/market/forecast-collect',json=REQUEST,headers=h).json()['state']=='SOURCE_BUSY'
    finally:main._COLLECT_SLOT.release()

def test_slow_source_does_not_block_health_on_same_event_loop(monkeypatch):
    import asyncio
    import httpx
    import time
    monkeypatch.setenv('SERVICE_TOKEN','private-test')
    def slow(*args,**kwargs):
        time.sleep(1)
        return subprocess.CompletedProcess('worker',0,'AISTOCK_FORECAST:{"state":"SOURCE_INVALID","candidate":null,"errorCode":"SOURCE_INVALID"}\n','')
    monkeypatch.setattr(subprocess,'run',slow)
    async def exercise():
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app),base_url='http://test') as client:
            started=time.monotonic()
            task=asyncio.create_task(client.post('/internal/v1/market/forecast-collect',json=REQUEST,headers={'X-Service-Token':'private-test'}))
            await asyncio.sleep(.02)
            healthy=await client.get('/health')
            elapsed=time.monotonic()-started
            await task
            assert healthy.status_code==200 and elapsed<.5
    asyncio.run(exercise())
