"""Seal the three audited local checkpoints; never publishes or overwrites a package."""
import argparse,hashlib,json,shutil,sys,tempfile
from pathlib import Path
import torch
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'backend'/'algorithm'))
from app.inference.artifacts import CONFIG,INSTRUMENTS,FEATURES,SOURCE,FEATURE,TARGET,COMMIT,runtime_hashes,digest_file,ArtifactStore

def main():
    p=argparse.ArgumentParser();p.add_argument('--checkpoint-directory',type=Path,required=True);p.add_argument('--output',type=Path,default=ROOT/'runtime'/'model-artifacts');a=p.parse_args()
    a.output.mkdir(parents=True,exist_ok=True)
    audited=json.loads((ROOT/'docs'/'evidence'/'transformer-readiness-20261002.json').read_text(encoding='utf-8-sig'))
    known={m['filename']:m['sha256'] for m in audited['models']};hashes=runtime_hashes();records=[]
    for seed in (42,43,44):
        source=a.checkpoint_directory/f'S3_one_year_{seed}_rmse6p2.pt'
        if not source.is_file() or source.is_symlink() or not 0<source.stat().st_size<=8388608 or digest_file(source)!=known[source.name]:raise ValueError('Audited checkpoint differs')
        ck=torch.load(source,map_location='cpu',weights_only=True)
        identity=hashlib.sha256(json.dumps({'checkpoint':known[source.name],'runtime':hashes},sort_keys=True,separators=(',',':')).encode()).hexdigest()[:16]
        artifact=f'transformer-s3-2023-seed{seed}-{identity}'
        data={'schemaVersion':1,'artifactId':artifact,'status':'CANDIDATE','sourceCommit':COMMIT,'checkpointHash':known[source.name],'checkpointBytes':source.stat().st_size,'config':ck['config'],'mean':ck['mean'],'std':ck['std'],'trainingCutoff':ck['cutoff'],'epoch':ck['epoch'],'instruments':INSTRUMENTS,'featureDefinition':FEATURE,'sourceDefinition':SOURCE,'targetDefinition':TARGET,'features':FEATURES,'clip':10,'runtimeHashes':hashes}
        payload=json.dumps(data,ensure_ascii=False,sort_keys=True,separators=(',',':'),allow_nan=False).encode();manifest_hash=hashlib.sha256(payload).hexdigest()
        final=a.output/artifact
        if final.exists():
            ArtifactStore(a.output).load(artifact,manifest_hash)
        else:
            temporary=Path(tempfile.mkdtemp(prefix='.prepare-',dir=a.output))
            shutil.copyfile(source,temporary/'checkpoint.pt');(temporary/'manifest.json').write_bytes(payload)
            temporary.rename(final)
            ArtifactStore(a.output).load(artifact,manifest_hash)
        records.append({'seed':seed,'artifactId':artifact,'manifestHash':manifest_hash,'checkpointHash':known[source.name],'status':'CANDIDATE','epoch':ck['epoch']})
    (a.output/'catalog.json').write_text(json.dumps({'schemaVersion':1,'artifacts':records},indent=2),encoding='utf-8')
    print(json.dumps(records,ensure_ascii=False))
if __name__=='__main__':main()
