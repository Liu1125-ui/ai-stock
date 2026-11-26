"""Reproduce archived results and live frozen inputs, never evaluates future accuracy."""
import argparse,csv,hashlib,json,sys,uuid
from datetime import date,datetime,timedelta,timezone
from decimal import Decimal,ROUND_HALF_UP
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def dec(value,scale):return format(Decimal(str(value)).quantize(Decimal(scale),rounding=ROUND_HALF_UP),'f')

def prepare(research,output):
    packets=[]
    for model in read(ROOT/'runtime/model-artifacts/catalog.json')['artifacts']:
        predictions=list(csv.DictReader((research/f'顺序改进验证/结果/S3_one_year_{model["seed"]}_rmse6p2_year2026.csv').open(encoding='utf-8-sig')))
        for code in ('SH600030','SH600276','SZ000002'):
            source=research/f'行情原始缓存/{code}_canonical.csv';raw=list(csv.DictReader(source.open(encoding='utf-8-sig')));lookup={r['date']:i for i,r in enumerate(raw)}
            rows=[r for r in predictions if r['stock']==code];origins=list(dict.fromkeys(r['origin'] for r in rows));indexes=[int(i*(len(origins)-1)/4) for i in range(5)]
            for index in indexes:
                origin=origins[index];at=lookup[origin];selected=raw[at-59:at+1];expected=sorted((r for r in rows if r['origin']==origin),key=lambda r:int(r['day']))
                assert len(selected)==60 and [r['target'] for r in expected]==[r['date'] for r in raw[at+1:at+6]]
                bars=[{'date':r['date'],'open':dec(r['open'],'.000001'),'high':dec(r['high'],'.000001'),'low':dec(r['low'],'.000001'),'close':dec(r['close'],'.000001'),'volumeShares':dec(r['volume'],'.0001'),'amountYuan':dec(r['amount'],'.000001'),'sourceAdjustmentRatio':dec(r['factor'],'.000000000001')} for r in selected]
                h=hashlib.sha256(json.dumps(bars,sort_keys=True,separators=(',',':')).encode()).hexdigest();source_hash=sha(source)
                # These UUIDs identify archived QA fixtures, not MS-02 business rows.
                fixture_id=str(uuid.uuid5(uuid.NAMESPACE_URL,'archived-qa:'+code+':'+h));raw_id=str(uuid.uuid5(uuid.NAMESPACE_URL,'archived-source:'+source_hash))
                targets=[r['target'] for r in expected]
                window={'instrumentId':code,'inputVersionId':fixture_id,'rawVersionId':raw_id,'rawContentHash':source_hash,'inputHash':h,'calendarHash':hashlib.sha256(json.dumps(targets).encode()).hexdigest(),'definition':'baostock-backward-close-over-raw-v1','provider':'baostock','priceBasis':'RAW','originDate':origin,'originRawClose':bars[-1]['close'],'capturedAt':datetime.now(timezone.utc).isoformat(),'freshness':'STALE','bars':bars,'targetDates':targets}
                packets.append({'scope':'ARCHIVED_RESEARCH_REPLAY','idsAreDerivedQaIdentities':True,'stock':code,'seed':model['seed'],'artifactId':model['artifactId'],'manifestHash':model['manifestHash'],'window':window,'expectedRatios':[r['pred_ratio'] for r in expected],'sourceHash':source_hash})
    for model in read(ROOT/'runtime/model-artifacts/catalog.json')['artifacts']:
        for code in ('SH600030','SH600276','SZ000002'):
            window=read(ROOT/f'runtime/sdd005a/live/sdd005a-20261003/window-{code}.json')
            packets.append({'scope':'MS02_FROZEN_INPUT','idsAreDerivedQaIdentities':False,'stock':code,'seed':model['seed'],'artifactId':model['artifactId'],'manifestHash':model['manifestHash'],'window':window})
    output.write_text(json.dumps(packets,ensure_ascii=False),encoding='utf-8')
    print(json.dumps({'packets':len(packets),'historical':45,'current':9}))

def verify(fixtures,models,output):
    sys.path.insert(0,str(Path('/app')))
    from app.inference.engine import InferenceEngine
    engine=InferenceEngine(models);checks=[]
    for packet in read(fixtures):
        result=engine.validate(packet['artifactId'],packet['manifestHash'],packet['window'])
        repeat=engine.validate(packet['artifactId'],packet['manifestHash'],packet['window'])
        assert result==repeat and result['validationOnly'] and [p['targetDate'] for p in result['points']]==packet['window']['targetDates']
        error=None
        if 'expectedRatios' in packet:
            error=max(abs(float(p['ratio'])-float(e)) for p,e in zip(result['points'],packet['expectedRatios']));assert error<=1e-6
        checks.append({'scope':packet['scope'],'idsAreDerivedQaIdentities':packet['idsAreDerivedQaIdentities'],'stock':packet['stock'],'seed':packet['seed'],'originDate':result['originDate'],'maxCsvDifference':error,'repeatExactlyEqual':True,'result':result})
    historical=[r for r in checks if r['scope']=='ARCHIVED_RESEARCH_REPLAY'];current=[r for r in checks if r['scope']=='MS02_FROZEN_INPUT']
    assert len(historical)==45 and len(current)==9
    report={'checkedAt':datetime.now(timezone.utc).isoformat(),'validationOnly':True,'published':False,'historicalWindows':45,'historicalPoints':225,'currentRuns':9,'currentPoints':45,'maxCsvDifference':max(r['maxCsvDifference'] for r in historical),'currentMaxClippedFraction':max(r['result']['clippedFraction'] for r in current),'checks':checks}
    output.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps({k:v for k,v in report.items() if k!='checks'}))

def main():
    p=argparse.ArgumentParser();p.add_argument('--prepare',action='store_true');p.add_argument('--research-root',type=Path);p.add_argument('--fixtures',type=Path,required=True);p.add_argument('--models',type=Path,default=Path('/model-artifacts'));p.add_argument('--output',type=Path);a=p.parse_args()
    if a.prepare:prepare(a.research_root,a.fixtures)
    else:verify(a.fixtures,a.models,a.output)
if __name__=='__main__':main()
