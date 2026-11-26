"""Single Baostock SDK boundary. Source errors are never represented as empty data."""
import hashlib
import json
import re
import threading
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal, InvalidOperation

_SESSION_LOCK = threading.Lock()
_FIELDS = 'date,open,high,low,close,volume,amount,tradestatus,code,peTTM,pbMRQ'

class BaostockProvider:
    def __init__(self, sdk=None):
        if sdk is None:
            import baostock as sdk
        self.sdk = sdk

    def collect(self, instrument_id: str, start: str, end: str) -> dict:
        if not re.fullmatch(r'(SH|SZ)\d{6}', instrument_id or ''):
            return self._error('SOURCE_INVALID')
        try:
            from_day, to_day = date.fromisoformat(start), date.fromisoformat(end)
            if from_day > to_day or (to_day - from_day).days > 1500:
                return self._error('SOURCE_INVALID')
        except (TypeError, ValueError):
            return self._error('SOURCE_INVALID')
        code = instrument_id[:2].lower() + '.' + instrument_id[2:]
        with _SESSION_LOCK:
            try:
                login = self.sdk.login()
                if str(login.error_code) != '0':
                    return self._sdk_error(login.error_code)
                result = self.sdk.query_history_k_data_plus(code, _FIELDS, start_date=start,
                             end_date=end, frequency='d', adjustflag='3')
                if str(result.error_code) != '0':
                    return self._sdk_error(result.error_code)
                if list(result.fields) != _FIELDS.split(','):
                    return self._error('SOURCE_INVALID')
                calendar_result = self.sdk.query_trade_dates(start_date=start, end_date=end)
                if str(calendar_result.error_code) != '0':
                    return self._sdk_error(calendar_result.error_code)
                if list(calendar_result.fields) != ['calendar_date', 'is_trading_day']:
                    return self._error('SOURCE_INVALID')
                calendar = []
                expected_day = from_day
                while calendar_result.next():
                    values = calendar_result.get_row_data()
                    if len(values) != 2 or values[0] != expected_day.isoformat() or values[1] not in ('0', '1') or expected_day > to_day:
                        return self._error('SOURCE_INVALID')
                    calendar.append(dict(date=values[0], open=values[1] == '1'))
                    expected_day += timedelta(days=1)
                if str(calendar_result.error_code) != '0':
                    return self._sdk_error(calendar_result.error_code)
                if expected_day != to_day + timedelta(days=1):
                    return self._error('SOURCE_INVALID')
                open_days = {r['date'] for r in calendar if r['open']}
                rows = []
                valuation = None
                while result.next():
                    fields = result.get_row_data()
                    if len(fields) != 11 or fields[8] != code:
                        return self._error('SOURCE_INVALID')
                    day, op, hi, lo, close, volume, amount, status = fields[:8]
                    try:
                        row_day = date.fromisoformat(day)
                        if not from_day <= row_day <= to_day or day not in open_days:
                            return self._error('SOURCE_INVALID')
                        values = [Decimal(x) if x else None for x in (op, hi, lo, close, volume, amount)]
                        if any(v is not None and not v.is_finite() for v in values):
                            return self._error('SOURCE_INVALID')
                        if status not in ('0','1'):
                            return self._error('SOURCE_INVALID')
                    except (ValueError, InvalidOperation):
                        return self._error('SOURCE_INVALID')
                    rows.append(dict(date=day, open=op, high=hi, low=lo, close=close,
                        volumeShares=volume or None, amountYuan=amount or None,
                        tradeStatus=int(status), sourceAdjustmentRatio=None))
                    if status == '1':
                        pe, pb = (self._ratio(value) for value in fields[9:])
                        valuation = dict(tradeDate=day, peTtm=pe, pbMrq=pb) if pe is not None or pb is not None else None
                if str(result.error_code) != '0':
                    return self._sdk_error(result.error_code)
                if len(rows)>1500 or len({r['date'] for r in rows})!=len(rows) or rows != sorted(rows,key=lambda r:r['date']):
                    return self._error('SOURCE_INVALID')
                payload = json.dumps({'bars': rows, 'calendar': calendar}, separators=(',', ':'), ensure_ascii=False)
                digest = hashlib.sha256(payload.encode('utf-8')).hexdigest()
                candidate = dict(operationId=None, instrumentId=instrument_id, ticker=instrument_id,
                    displayName=instrument_id, displayNameVerified=False, listingStatus='UNKNOWN',
                    provider='baostock', priceBasis='RAW', fetchedAt=datetime.now(timezone.utc).isoformat(),
                    sourceVersionId=digest, sourcePayloadHash=digest, bars=rows, calendar=calendar, expectedRevision=None)
                return {'state':'AVAILABLE' if rows else 'LEGITIMATE_EMPTY','candidate':candidate,'errorCode':None,'retryAt':None,'valuation':valuation}
            except TimeoutError:
                return self._error('SOURCE_TIMEOUT')
            except OSError:
                return self._error('SOURCE_UNAVAILABLE')
            except Exception:
                return self._error('SOURCE_INVALID')
            finally:
                try:
                    self.sdk.logout()
                except Exception:
                    pass

    @staticmethod
    def _ratio(value):
        if not isinstance(value, str) or not re.fullmatch(r'-?[0-9]{1,12}(\.[0-9]{1,8})?', value):
            return None
        number = Decimal(value)
        return format(number.normalize(), 'f') if number else None

    @staticmethod
    def _sdk_error(code):
        code = str(code)
        if code in {'10002003', '10002006', '10002008'}:
            return BaostockProvider._error('SOURCE_TIMEOUT')
        if code in {'10002001', '10002002', '10002004', '10002005', '10002007', '10005001'}:
            return BaostockProvider._error('SOURCE_UNAVAILABLE')
        if code == '10001005':
            return BaostockProvider._error('SOURCE_BUSY')
        return BaostockProvider._error('SOURCE_INVALID')

    @staticmethod
    def _error(code):
        return {'state':code,'candidate':None,'errorCode':code,'retryAt':None}
