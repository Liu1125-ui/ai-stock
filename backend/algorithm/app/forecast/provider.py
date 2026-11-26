"""Paired source evidence for an immutable forecast input, never a price forecast."""
import hashlib
import json
import re
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal, InvalidOperation
from uuid import UUID
from app.market.providers import BaostockProvider, _SESSION_LOCK
FIELDS='date,code,open,high,low,close,volume,amount,adjustflag,tradestatus'
DEFINITION='baostock-backward-close-over-raw-v1'

def validate_request(req):
    if not isinstance(req,dict) or set(req)!=set(('operationId','instrumentId','rawVersionId','start','end','calendarThrough')):
        raise ValueError('Invalid request')
    if not re.fullmatch(r'[A-Za-z0-9_-]{1,128}',req.get('operationId','')) or not re.fullmatch(r'(SH|SZ)\d{6}',req.get('instrumentId','')):
        raise ValueError('Invalid identity')
    UUID(req['rawVersionId'])
    start,end,through=[date.fromisoformat(req[k]) for k in ('start','end','calendarThrough')]
    if not date(1990,1,1)<=start<=end<=datetime.now(timezone.utc).astimezone(timezone(timedelta(hours=8))).date() or (end-start).days>1500 or not 5<=(through-end).days<=90:
        raise ValueError('Invalid range')
    return start,end,through

class SourceFailure(Exception):
    def __init__(self,code):self.code=code

class ForecastSource:
    def __init__(self,sdk=None):
        if sdk is None:
            import baostock as sdk
        self.sdk=sdk
    def read(self,result,fields):
        self.check(result)
        if list(result.fields)!=fields.split(','):raise ValueError('Source fields mismatch')
        rows=[]
        while result.next():
            row=result.get_row_data()
            if len(row)!=len(result.fields):raise ValueError('Source row mismatch')
            rows.append(row)
            if len(rows)>3000:raise ValueError('Source too large')
        self.check(result)
        return rows
    def check(self,result):
        if str(result.error_code)!='0':
            raise SourceFailure(BaostockProvider._sdk_error(result.error_code)['state'])
    def parse_request(self,req):return validate_request(req)
    def collect(self,req):
        try:start,end,through=self.parse_request(req)
        except (ValueError,TypeError,KeyError,AttributeError):return self.error('SOURCE_INVALID')
        code=req['instrumentId'][:2].lower()+'.'+req['instrumentId'][2:]
        with _SESSION_LOCK:
            try:
                self.check(self.sdk.login())
                pairs=[]
                for flag in ('3','1'):
                    rows=self.read(self.sdk.query_history_k_data_plus(code,FIELDS,start_date=start.isoformat(),end_date=end.isoformat(),frequency='d',adjustflag=flag),FIELDS)
                    if not rows:raise ValueError('No paired bars')
                    previous=None
                    for r in rows:
                        day=date.fromisoformat(r[0])
                        if r[1]!=code or r[8]!=flag or r[9] not in ('0','1') or not start<=day<=end or previous is not None and day<=previous:
                            raise ValueError('Source identity/order mismatch')
                        nums=[Decimal(v) for v in r[2:8]]
                        if not all(v.is_finite() for v in nums) or any(v<=0 for v in nums[:4]) or any(v<0 for v in nums[4:]):
                            raise ValueError('Source numeric mismatch')
                        previous=day
                    pairs.append(rows)
                raw,adj=pairs
                if [r[0] for r in raw]!=[r[0] for r in adj]:raise ValueError('Unpaired dates')
                days=self.read(self.sdk.query_trade_dates(start_date=start.isoformat(),end_date=through.isoformat()),'calendar_date,is_trading_day')
                expected=start;calendar=[]
                for r in days:
                    if r[0]!=expected.isoformat() or r[1] not in ('0','1') or expected>through:raise ValueError('Calendar incomplete')
                    calendar.append({'date':r[0],'open':r[1]=='1'});expected+=timedelta(days=1)
                if expected!=through+timedelta(days=1):raise ValueError('Calendar incomplete')
                open_days={r['date'] for r in calendar if r['open']}
                if any(r[0] not in open_days for r in raw):raise ValueError('Closed date has bar')
                bars=[]
                for r,a in zip(raw,adj):
                    bars.append({'raw':{'date':r[0],'open':r[2],'high':r[3],'low':r[4],'close':r[5],
                        'volumeShares':r[6],'amountYuan':r[7],'tradeStatus':int(r[9]),'sourceAdjustmentRatio':None},'backwardClose':a[5]})
                payload=json.dumps({'raw':raw,'adjusted':adj,'calendar':days},ensure_ascii=False,separators=(',',':'))
                candidate={k:req[k] for k in ('operationId','instrumentId','rawVersionId')}
                candidate.update(provider='baostock',definition=DEFINITION,fetchedAt=datetime.now(timezone.utc).isoformat(),
                    sourcePayloadHash=hashlib.sha256(payload.encode()).hexdigest(),bars=bars,calendar=calendar)
                return {'state':'AVAILABLE','candidate':candidate,'errorCode':None}
            except SourceFailure as error:return self.error(error.code)
            except TimeoutError:return self.error('SOURCE_TIMEOUT')
            except OSError:return self.error('SOURCE_UNAVAILABLE')
            except (ValueError,TypeError,InvalidOperation,KeyError,AttributeError):return self.error('SOURCE_INVALID')
            finally:
                try:self.sdk.logout()
                except Exception:pass
    @staticmethod
    def error(code):return {'state':code,'candidate':None,'errorCode':code}
