"""Read a research SQLite snapshot and write controlled RAW candidate files.
The old research factor is deliberately omitted: its float precision and anchor are not verified.
"""
import argparse
import hashlib
import json
import re
import sqlite3
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path

DEFAULT_SOURCE = Path(r'E:\aiStock\实验与测试\持续更新可行性\state\updates.sqlite')
DEFAULT_STOCKS = ('SH600030','SH600276','SZ000002')

def number(value, places, digits):
    if not isinstance(value, Decimal) or not value.is_finite(): raise ValueError('invalid decimal')
    if value.as_tuple().exponent < -places or len(value.copy_abs().to_integral_value().as_tuple().digits)>digits-places:
        raise ValueError('source decimal exceeds database precision')
    return str(value)

def prepare(source: Path, stocks=DEFAULT_STOCKS):
    source=Path(source).resolve(strict=True)
    if source.stat().st_size>100_000_000: raise ValueError('unexpected source size')
    uri=source.as_uri()+'?mode=ro'
    with sqlite3.connect(uri,uri=True,timeout=5) as db:
        db.execute('PRAGMA query_only=ON')
        db.execute('BEGIN')
        result=[]
        for stock in stocks:
            if not re.fullmatch(r'(SH|SZ)\d{6}',stock):raise ValueError('invalid stock identity')
            record=db.execute('SELECT v.id,v.source,v.stock,v.payload,v.created,v.basis,l.checked_through FROM versions v JOIN latest l ON l.version_id=v.id AND l.stock=v.stock AND l.source=v.source WHERE l.stock=? AND l.source=?',(stock,'baostock')).fetchone()
            if record is None:raise ValueError('source version absent')
            version,provider,instrument,payload,created,basis,checked=record
            if basis!='baostock_backward_over_raw' or provider!='baostock' or instrument!=stock:raise ValueError('unexpected source basis')
            source_digest=hashlib.sha256((provider+instrument+basis+payload).encode()).hexdigest()
            if version!=source_digest:raise ValueError('source version hash mismatch')
            payload_digest=hashlib.sha256(payload.encode()).hexdigest()
            rows=json.loads(payload,parse_float=Decimal,parse_int=Decimal)
            if not isinstance(rows,list) or not 1<=len(rows)<=1500:raise ValueError('source rows invalid')
            bars=[];dates=[]
            for row in rows:
                if set(row)!=set(('amount','close','date','factor','high','low','open','tradestatus','volume')):raise ValueError('source fields mismatch')
                day=datetime.strptime(row['date'],'%Y-%m-%d').date().isoformat();dates.append(day)
                status=int(row['tradestatus'])
                if status not in (0,1) or row['tradestatus'] != status:raise ValueError('trade status invalid')
                # Prices are raw in the research payload; factor is a separately stored, unverified float ratio.
                bar={'date':day,'open':number(row['open'],6,20),'high':number(row['high'],6,20),'low':number(row['low'],6,20),'close':number(row['close'],6,20),
                     'volumeShares':number(row['volume'],4,20) if row['volume'] is not None else None,
                     'amountYuan':number(row['amount'],6,24) if row['amount'] is not None else None,
                     'tradeStatus':status,'sourceAdjustmentRatio':None}
                prices=[Decimal(bar[key]) for key in ('open','high','low','close')]
                if min(prices)<=0 or prices[1]<max(prices) or prices[2]>min(prices) or (status==1 and (bar['volumeShares'] is None or bar['amountYuan'] is None)):
                    raise ValueError('invalid OHLC or trading quantity')
                bars.append(bar)
            if sorted(dates)!=dates or len(set(dates))!=len(dates) or checked!=dates[-1]:raise ValueError('source chronology mismatch')
            fetched=datetime.fromtimestamp(created,timezone.utc).isoformat().replace('+00:00','Z')
            candidate={'operationId':'sqlite-'+stock+'-'+version[:32],'instrumentId':stock,'ticker':stock,'displayName':stock,
                'displayNameVerified':False,'listingStatus':'UNKNOWN','provider':'baostock','priceBasis':'RAW','fetchedAt':fetched,
                'sourceVersionId':version,'sourcePayloadHash':payload_digest,'bars':bars,'calendar':[],'expectedRevision':None}
            result.append({'candidate':candidate,'evidence':{'source':'historical-research-sqlite','originalVersionId':version,'sourcePayloadHash':payload_digest,
                'originalBasis':basis,'checkedThrough':checked,'rows':len(bars),'dateStart':dates[0],'dateEnd':dates[-1],
                'calendar':'not contained in source version','factor':'omitted: float precision and anchor unverified'}})
        db.execute('COMMIT')
        return result

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--source',type=Path,default=DEFAULT_SOURCE);parser.add_argument('--output',type=Path,default=Path('runtime/sdd003/candidates'))
    args=parser.parse_args();items=prepare(args.source);args.output.mkdir(parents=True,exist_ok=True)
    for item in items:
        stock=item['candidate']['instrumentId']
        (args.output/(stock+'.json')).write_text(json.dumps(item['candidate'],ensure_ascii=False,separators=(',',':')),encoding='utf-8')
    (args.output/'manifest.json').write_text(json.dumps([item['evidence']|{'instrumentId':item['candidate']['instrumentId']} for item in items],ensure_ascii=False,indent=2),encoding='utf-8')
    print('Prepared',len(items),'historical RAW candidates; latest source date',items[0]['evidence']['checkedThrough'])

if __name__=='__main__':main()
