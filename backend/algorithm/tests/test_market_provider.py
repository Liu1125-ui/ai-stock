import threading
from concurrent.futures import ThreadPoolExecutor
from app.market.providers import BaostockProvider

class Result:
    def __init__(self, rows, fields=None, error_code='0'):
        self.error_code=error_code; self.error_msg='source rejected'; self.fields=fields or ['date','open','high','low','close','volume','amount','tradestatus','code','peTTM','pbMRQ']; self.rows=list(rows); self.at=-1
    def next(self):
        self.at+=1; return self.at<len(self.rows)
    def get_row_data(self): return self.rows[self.at]

class SDK:
    def __init__(self, rows=(), error='0', login='0'):
        self.query=Result(rows,error_code=error); self.login_code=login; self.codes=[]; self.logouts=0; self.active=0; self.highwater=0;self.lock=threading.Lock()
    def query_trade_dates(self, start_date, end_date):
        from datetime import date, timedelta
        first, last = date.fromisoformat(start_date), date.fromisoformat(end_date)
        rows=[]
        while first <= last:
            rows.append([first.isoformat(), '1' if first.weekday()<5 else '0'])
            first += timedelta(days=1)
        return Result(rows, fields=['calendar_date','is_trading_day'])
    def login(self): return Result([],error_code=self.login_code)
    def logout(self): self.logouts+=1
    def query_history_k_data_plus(self,code,fields,**kwargs):
        with self.lock:
            self.active+=1; self.highwater=max(self.active,self.highwater)
        try:
            import time;time.sleep(.02);self.codes.append((code,kwargs['adjustflag']));return Result([list(r)+[code,'',''] if len(r)==8 else r for r in self.query.rows],fields=self.query.fields,error_code=self.query.error_code)
        finally:
            with self.lock:self.active-=1

def test_source_mapping_and_decimal_strings():
    sdk=SDK([['2026-09-24','10.1','10.3','9.9','10.2','1200','12240','1']]);result=BaostockProvider(sdk).collect('SH600030','2026-09-01','2026-09-24');assert result['state']=='AVAILABLE';assert sdk.codes==[('sh.600030','3')];assert result['candidate']['bars'][0]['close']=='10.2';assert result['candidate']['bars'][0]['volumeShares']=='1200';assert sdk.logouts==1

def test_query_error_is_not_legitimate_empty():
    sdk=SDK(error='10004006');result=BaostockProvider(sdk).collect('SZ000002','2026-09-01','2026-09-24');assert result['state']=='SOURCE_INVALID';assert result['candidate'] is None;assert sdk.logouts==1

def test_empty_login_failure_and_illegal_code():
    assert BaostockProvider(SDK()).collect('SZ000002','2026-09-01','2026-09-24')['state']=='LEGITIMATE_EMPTY'
    sdk=SDK(login='100');assert BaostockProvider(sdk).collect('SH600030','2026-09-01','2026-09-24')['state']=='SOURCE_INVALID';assert sdk.logouts==1
    assert BaostockProvider(SDK()).collect('600030','2026-09-01','2026-09-24')['state']=='SOURCE_INVALID'

def test_global_sdk_session_is_serialized():
    sdk=SDK([['2026-09-24','10','10','10','10','1','10','1']]);provider=BaostockProvider(sdk)
    with ThreadPoolExecutor(max_workers=2) as pool:list(pool.map(lambda _:provider.collect('SH600030','2026-09-01','2026-09-24'),range(2)))
    assert sdk.highwater==1

def test_calendar_is_continuous_and_includes_closed_days():
    sdk=SDK([['2026-09-24','10','10','10','10','1','10','1']])
    result=BaostockProvider(sdk).collect('SH600030','2026-09-24','2026-09-27')
    assert result['state']=='AVAILABLE'
    assert result['candidate']['calendar']==[
        {'date':'2026-09-24','open':True},{'date':'2026-09-25','open':True},
        {'date':'2026-09-26','open':False},{'date':'2026-09-27','open':False}]

def test_bad_calendar_and_closed_day_bar_never_publish():
    sdk=SDK([['2026-09-24','10','10','10','10','1','10','1']])
    sdk.query_trade_dates=lambda **_:Result([],fields=['calendar_date','is_trading_day'],error_code='1')
    assert BaostockProvider(sdk).collect('SH600030','2026-09-24','2026-09-24')['state']=='SOURCE_INVALID'
    sdk.query_trade_dates=lambda **_:Result([['2026-09-24','0']],fields=['calendar_date','is_trading_day'])
    assert BaostockProvider(sdk).collect('SH600030','2026-09-24','2026-09-24')['state']=='SOURCE_INVALID'

def test_partial_calendar_and_out_of_range_bar_rejected():
    sdk=SDK([['2026-09-23','10','10','10','10','1','10','1']])
    assert BaostockProvider(sdk).collect('SH600030','2026-09-24','2026-09-24')['state']=='SOURCE_INVALID'
    sdk=SDK([['2026-09-24','10','10','10','10','1','10','1']])
    sdk.query_trade_dates=lambda **_:Result([['2026-09-24','1']],fields=['calendar_date','is_trading_day'])
    assert BaostockProvider(sdk).collect('SH600030','2026-09-24','2026-09-25')['state']=='SOURCE_INVALID'

def test_sdk_transport_errors_remain_retryable_at_every_boundary():
    assert BaostockProvider(SDK(login='10002007')).collect('SH600030','2026-09-24','2026-09-24')['state']=='SOURCE_UNAVAILABLE'
    assert BaostockProvider(SDK(error='10002008')).collect('SH600030','2026-09-24','2026-09-24')['state']=='SOURCE_TIMEOUT'
    sdk=SDK([['2026-09-24','10','10','10','10','1','10','1']])
    sdk.query_trade_dates=lambda **_:Result([],fields=['calendar_date','is_trading_day'],error_code='10002002')
    assert BaostockProvider(sdk).collect('SH600030','2026-09-24','2026-09-24')['state']=='SOURCE_UNAVAILABLE'

def test_empty_collection_retains_range_and_calendar_evidence():
    r=BaostockProvider(SDK()).collect('SH600030','2026-09-24','2026-09-25')
    assert r['state']=='LEGITIMATE_EMPTY'
    assert r['candidate']['bars']==[]
    assert r['candidate']['calendar']==[{'date':'2026-09-24','open':True},{'date':'2026-09-25','open':True}]

def test_auxiliary_ratios_preserve_negative_values_and_never_change_raw_hash():
    base = ["2026-09-24", "10", "10", "10", "10", "1", "10", "1"]
    previous = ["2026-09-23", "10", "10", "10", "10", "1", "10", "1"]
    first = BaostockProvider(SDK([previous + ["sh.600030", "9", "3"], base + ["sh.600030", "-15.2500", "0"]])).collect(
        "SH600030", "2026-09-23", "2026-09-24")
    second = BaostockProvider(SDK([previous + ["sh.600030", "9", "3"], base + ["sh.600030", "NaN", "2.01"]])).collect(
        "SH600030", "2026-09-23", "2026-09-24")
    assert first["valuation"] == {"tradeDate": "2026-09-24", "peTtm": "-15.25", "pbMrq": None}
    assert second["valuation"] == {"tradeDate": "2026-09-24", "peTtm": None, "pbMrq": "2.01"}
    assert first["candidate"]["sourcePayloadHash"] == second["candidate"]["sourcePayloadHash"]


def test_wrong_source_symbol_cannot_publish_for_the_requested_stock():
    row = ["2026-09-24", "10", "10", "10", "10", "1", "10", "1", "sh.600000", "11", "1.5"]
    result = BaostockProvider(SDK([row])).collect("SH600030", "2026-09-24", "2026-09-24")
    assert result["state"] == "SOURCE_INVALID"
    assert result.get("valuation") is None
