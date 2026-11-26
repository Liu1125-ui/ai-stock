"""Verified source calendar, independent of any stock price snapshot."""
import hashlib
import re
from datetime import date, datetime, timedelta, timezone
from app.market.providers import BaostockProvider, _SESSION_LOCK


def validate_request(req):
    if not isinstance(req, dict) or set(req) != {'operationId', 'start', 'end'}:
        raise ValueError('Invalid calendar request')
    if not isinstance(req['operationId'], str) or not re.fullmatch(r'[A-Za-z0-9_-]{1,128}', req['operationId']):
        raise ValueError('Invalid operation')
    if any(not isinstance(req[k], str) or not re.fullmatch(r'\d{4}-\d{2}-\d{2}', req[k]) for k in ('start', 'end')):
        raise ValueError('Invalid date format')
    start, end = date.fromisoformat(req['start']), date.fromisoformat(req['end'])
    today = datetime.now(timezone(timedelta(hours=8))).date()
    if not today-timedelta(days=366) <= start <= end <= today+timedelta(days=90) or (end-start).days >= 400:
        raise ValueError('Invalid calendar range')
    return start, end


class CalendarSource:
    def __init__(self, sdk=None):
        if sdk is None:
            import baostock as sdk
        self.sdk = sdk

    def collect(self, req):
        try:
            start, end = validate_request(req)
        except (ValueError, TypeError, KeyError):
            return self.error(req.get('operationId') if isinstance(req, dict) else None, 'SOURCE_INVALID')
        with _SESSION_LOCK:
            try:
                login = self.sdk.login()
                if str(login.error_code) != '0':
                    return self.error(req['operationId'], BaostockProvider._sdk_error(login.error_code)['state'])
                result = self.sdk.query_trade_dates(start_date=start.isoformat(), end_date=end.isoformat())
                if str(result.error_code) != '0':
                    return self.error(req['operationId'], BaostockProvider._sdk_error(result.error_code)['state'])
                if list(result.fields) != ['calendar_date', 'is_trading_day']:
                    raise ValueError('Invalid calendar fields')
                days, expected, canonical = [], start, []
                while result.next():
                    row = result.get_row_data()
                    if len(row) != 2 or row[0] != expected.isoformat() or row[1] not in ('0', '1') or expected > end:
                        raise ValueError('Incomplete calendar')
                    days.append({'date': row[0], 'open': row[1] == '1'})
                    canonical.append(f'{row[0]}:{row[1]}\n')
                    expected += timedelta(days=1)
                if str(result.error_code) != '0':
                    return self.error(req['operationId'], BaostockProvider._sdk_error(result.error_code)['state'])
                if expected != end+timedelta(days=1):
                    raise ValueError('Incomplete calendar')
                candidate = dict(provider='baostock', start=start.isoformat(), end=end.isoformat(),
                    fetchedAt=datetime.now(timezone.utc).isoformat(),
                    sourcePayloadHash=hashlib.sha256(''.join(canonical).encode()).hexdigest(), days=days)
                return dict(operationId=req['operationId'], state='AVAILABLE', candidate=candidate, errorCode=None)
            except TimeoutError:
                return self.error(req['operationId'], 'SOURCE_TIMEOUT')
            except OSError:
                return self.error(req['operationId'], 'SOURCE_UNAVAILABLE')
            except Exception:
                return self.error(req['operationId'], 'SOURCE_INVALID')
            finally:
                try:
                    self.sdk.logout()
                except Exception:
                    pass

    @staticmethod
    def error(operation, code):
        return dict(operationId=operation, state=code, candidate=None, errorCode=code)
