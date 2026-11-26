import hashlib,json,sqlite3
from pathlib import Path
from app.market.prepare import prepare

def test_research_snapshot_becomes_unadjusted_candidate_with_original_time(tmp_path):
    file=tmp_path/'sample.sqlite';c=sqlite3.connect(file)
    c.execute('CREATE TABLE versions(id text PRIMARY KEY,source text,stock text,payload text,created real,basis text)')
    c.execute('CREATE TABLE latest(source text,stock text,version_id text,checked_through text)')
    payload=json.dumps([dict(date='2026-09-24',open=10.1,high=10.2,low=10.0,close=10.1,volume=100,amount=1010,factor=123.456789123456789,tradestatus=1)])
    basis='baostock_backward_over_raw';digest=hashlib.sha256(('baostock'+'SH600030'+basis+payload).encode()).hexdigest()
    c.execute('INSERT INTO versions VALUES(?,?,?,?,?,?)',(digest,'baostock','SH600030',payload,1790495208.450956,basis))
    c.execute('INSERT INTO latest VALUES(?,?,?,?)',('baostock','SH600030',digest,'2026-09-24'));c.commit();c.close()
    prepared=prepare(file,('SH600030',))
    assert len(prepared)==1
    candidate=prepared[0]['candidate'];assert candidate['priceBasis']=='RAW';assert candidate['calendar']==[]
    assert candidate['sourceVersionId']==digest;assert candidate['fetchedAt'].startswith('2026-09-27T')
    assert candidate['bars'][0]['close']=='10.1';assert candidate['bars'][0]['sourceAdjustmentRatio'] is None
    assert prepared[0]['evidence']['checkedThrough']=='2026-09-24'
