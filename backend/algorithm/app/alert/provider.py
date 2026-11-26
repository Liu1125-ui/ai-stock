"""Historical-only alert source; forecast request semantics remain unchanged."""
from datetime import date, datetime, timedelta, timezone
import re
from uuid import UUID
from app.forecast.provider import ForecastSource

def validate_request(req):
    if not isinstance(req,dict) or set(req)!=set(('operationId','instrumentId','rawVersionId','start','end','calendarThrough')):
        raise ValueError('Invalid request')
    if not isinstance(req['operationId'],str) or not re.fullmatch(r'[A-Za-z0-9_-]{1,128}',req['operationId']) or not isinstance(req['instrumentId'],str) or not re.fullmatch(r'(SH|SZ)[0-9]{6}',req['instrumentId']):
        raise ValueError('Invalid identity')
    if not isinstance(req['rawVersionId'],str):raise ValueError('Invalid version')
    UUID(req['rawVersionId'])
    if any(not isinstance(req[k],str) or not re.fullmatch(r'[0-9]{4}-[0-9]{2}-[0-9]{2}',req[k]) for k in ('start','end','calendarThrough')):
        raise ValueError('Invalid date')
    start,end,through=[date.fromisoformat(req[k]) for k in ('start','end','calendarThrough')]
    today=datetime.now(timezone(timedelta(hours=8))).date()
    if not date(1990,1,1)<=start<end<=today or through!=end or (end-start).days>=400:
        raise ValueError('Invalid historical range')
    return start,end,through

class AlertSource(ForecastSource):
    def parse_request(self,req):return validate_request(req)
