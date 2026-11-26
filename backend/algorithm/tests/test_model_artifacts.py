import copy
import hashlib
import json
from pathlib import Path
from datetime import date,timedelta
import numpy as np
import pytest
import torch
ARTIFACT='transformer-s3-2023-seed42'
CONFIG={'task_name':'long_term_forecast','seq_len':60,'label_len':30,'pred_len':5,'enc_in':6,'dec_in':6,'c_out':1,'d_model':32,'n_heads':4,'e_layers':1,'d_layers':1,'d_ff':64,'dropout':.1,'factor':3,'embed':'timeF','freq':'d','activation':'gelu'}

@pytest.fixture
def window():
    start=date(2026,7,1)
    bars=[{'date':(start+timedelta(days=i)).isoformat(),'open':'10','high':'11','low':'9','close':'10','volumeShares':'100','amountYuan':'1000','sourceAdjustmentRatio':'2'} for i in range(60)]
    return {'instrumentId':'SH600030','inputVersionId':'11111111-1111-1111-1111-111111111111','rawVersionId':'22222222-2222-2222-2222-222222222222','rawContentHash':'a'*64,'inputHash':'b'*64,'calendarHash':'c'*64,'definition':'baostock-backward-close-over-raw-v1','provider':'baostock','priceBasis':'RAW','originDate':bars[-1]['date'],'originRawClose':'10','capturedAt':'2026-10-03T00:00:00Z','freshness':'STALE','bars':bars,'targetDates':[(start+timedelta(days=i)).isoformat() for i in range(60,65)]}

@pytest.fixture
def bundle(tmp_path):
    from app.inference.vendor.model import Model
    from app.inference.artifacts import runtime_hashes
    from types import SimpleNamespace
    torch.manual_seed(7)
    model=Model(SimpleNamespace(**CONFIG))
    with torch.no_grad():model.decoder.projection.weight.zero_();model.decoder.projection.bias.fill_(1.02)
    root=tmp_path/'models';folder=root/ARTIFACT;folder.mkdir(parents=True)
    ck={'state_dict':model.state_dict(),'config':CONFIG,'mean':[0.]*6,'std':[1.]*6,'mode':'absolute','cutoff':'2023-12-31','epoch':3}
    torch.save(ck,folder/'checkpoint.pt')
    data={'schemaVersion':1,'artifactId':ARTIFACT,'status':'CANDIDATE','sourceCommit':'4e938a1767106324dd753b2a44832bf870a0252e','checkpointHash':hashlib.sha256((folder/'checkpoint.pt').read_bytes()).hexdigest(),'checkpointBytes':(folder/'checkpoint.pt').stat().st_size,'config':CONFIG,'mean':ck['mean'],'std':ck['std'],'trainingCutoff':ck['cutoff'],'epoch':ck['epoch'],'instruments':['SH600030','SH600276','SZ000002'],'featureDefinition':'baostock-six-price-volume-v1','sourceDefinition':'baostock-backward-close-over-raw-v1','targetDefinition':'adjusted-close-relative-origin-v1','features':['open','high','low','close','vwap','volume'],'clip':10,'runtimeHashes':runtime_hashes()}
    (folder/'manifest.json').write_text(json.dumps(data,separators=(',',':')),encoding='utf-8')
    return root,folder,data

def manifest_hash(folder):return hashlib.sha256((folder/'manifest.json').read_bytes()).hexdigest()

def test_safe_strict_model_load_and_five_decimal_points(bundle,window):
    from app.inference.engine import InferenceEngine
    root,folder,_=bundle
    result=InferenceEngine(root).validate(ARTIFACT,manifest_hash(folder),window)
    assert result['validationOnly'] is True and len(result['points'])==5
    assert result['points'][0]['referencePrice']=='10.200000' and result['points'][0]['displayReferencePrice']=='10.20'
    assert result['points'][0]['targetDate']==window['targetDates'][0]
    assert result['inputHash']==window['inputHash'] and result['freshness']=='STALE'
    assert result==InferenceEngine(root).validate(ARTIFACT,manifest_hash(folder),window)

def test_price_and_volume_conversion_has_matching_direction(window):
    from app.inference.contracts import prepare_window
    tensor,_=prepare_window(window,['SH600030'])
    np.testing.assert_array_equal(tensor[0],np.array([20,22,18,20,20,50],dtype=np.float32))

def test_bad_id_hash_or_corrupted_weight_never_loads(bundle):
    from app.inference.artifacts import ArtifactStore,InferenceError
    root,folder,_=bundle;store=ArtifactStore(root)
    for bad in ('../checkpoint.pt','/tmp/weights','transformer-s3-2023-seed99'):
        with pytest.raises(InferenceError):store.load(bad,manifest_hash(folder))
    with pytest.raises(InferenceError):store.load(ARTIFACT,'0'*64)
    with (folder/'checkpoint.pt').open('ab') as f:f.write(b'changed')
    with pytest.raises(InferenceError):store.load(ARTIFACT,manifest_hash(folder))

def test_manifest_config_scaling_runtime_changes_are_rejected(bundle):
    from app.inference.artifacts import ArtifactStore,InferenceError
    root,folder,data=bundle
    for change in ({'std':[0.]*6},{'config':{**CONFIG,'pred_len':20}},{'runtimeHashes':{'wrong':'a'*64}},{'mean':[float('nan')]*6},{'features':list(reversed(data['features']))},{'status':'PUBLISHED'}):
        (folder/'manifest.json').write_text(json.dumps({**data,**change}),encoding='utf-8')
        with pytest.raises(InferenceError):ArtifactStore(root).load(ARTIFACT,manifest_hash(folder))

@pytest.mark.parametrize('field,value',[('open','1E+100000000'),('close','1E-100000000'),('sourceAdjustmentRatio',None),('volumeShares','0'),('amountYuan','NaN')])
def test_bad_numbers_rejected_before_tensor_or_hash(window,field,value):
    from app.inference.contracts import prepare_window
    from app.inference.artifacts import InferenceError
    window['bars'][0][field]=value
    with pytest.raises(InferenceError):prepare_window(window,['SH600030'])

def test_bad_dates_origin_definition_and_unsupported_instrument_reject(window):
    from app.inference.contracts import prepare_window
    from app.inference.artifacts import InferenceError
    for mutate in (lambda w:w['bars'][1].update(date=w['bars'][0]['date']),lambda w:w.update(originRawClose='11'),lambda w:w.update(definition='unknown'),lambda w:w.update(instrumentId='SH600000'),lambda w:w.update(targetDates=[w['originDate']]*5)):
        bad=copy.deepcopy(window);mutate(bad)
        with pytest.raises(InferenceError):prepare_window(bad,['SH600030'])

def test_checkpoint_config_cannot_disagree_with_manifest(bundle):
    from app.inference.artifacts import ArtifactStore,InferenceError
    root,folder,data=bundle
    ck=torch.load(folder/'checkpoint.pt',weights_only=True);ck['mean']=[1.]*6;torch.save(ck,folder/'checkpoint.pt')
    data['checkpointHash']=hashlib.sha256((folder/'checkpoint.pt').read_bytes()).hexdigest();data['checkpointBytes']=(folder/'checkpoint.pt').stat().st_size
    (folder/'manifest.json').write_text(json.dumps(data),encoding='utf-8')
    with pytest.raises(InferenceError):ArtifactStore(root).load(ARTIFACT,manifest_hash(folder))


def test_rolling_artifact_uses_new_cutoff_and_preserves_legacy(bundle,window):
    from app.inference.artifact_contract import rolling_artifact_id
    from app.inference.artifacts import ArtifactStore,InferenceError,digest_file
    from app.inference.rolling_artifacts import VersionedArtifactStore,rolling_runtime_hashes
    from app.inference.worker import engine_for

    root,legacy,original=bundle
    checkpoint=torch.load(legacy/'checkpoint.pt',weights_only=True)
    checkpoint.update(cutoff='2024-12-31',datasetHash='a'*64,seed=42)
    staged=root/'staged'
    staged.mkdir()
    torch.save(checkpoint,staged/'checkpoint.pt')
    checkpoint_hash=digest_file(staged/'checkpoint.pt')
    training={
        'datasetHash':'a'*64,'seed':42,
        'train':{'from':'2023-01-01','through':'2024-12-31'},
        'validation':{'from':'2025-01-01','through':'2025-06-30'},
        'holdout':{'from':'2025-07-01','through':'2025-12-31'},
    }
    artifact=rolling_artifact_id(training,checkpoint_hash)
    folder=staged.rename(root/artifact)
    data={**original,'schemaVersion':2,'artifactId':artifact,'checkpointHash':checkpoint_hash,
          'checkpointBytes':(folder/'checkpoint.pt').stat().st_size,'trainingCutoff':'2024-12-31',
          'trainingData':training,'runtimeHashes':rolling_runtime_hashes()}

    def write(value):
        (folder/'manifest.json').write_text(json.dumps(value),encoding='utf-8')
        return manifest_hash(folder)

    digest=write(data)
    assert len(engine_for(root).validate(artifact,digest,window)['points'])==5
    assert VersionedArtifactStore(root).load(artifact,digest)[0]['trainingData']==training
    assert ArtifactStore(root).load(ARTIFACT,manifest_hash(legacy))[0]['schemaVersion']==1

    overlapping={**training,'validation':{'from':'2024-12-31','through':'2025-06-30'}}
    for replacement in ({'trainingData':overlapping},
                        {'trainingData':{**training,'datasetHash':'b'*64}},
                        {'trainingCutoff':'2024-12-30'},
                        {'schemaVersion':1}):
        with pytest.raises(InferenceError):
            VersionedArtifactStore(root).load(artifact,write({**data,**replacement}))
