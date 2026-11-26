"""Bounded immutable candidate artifacts; no publication authority."""
import hashlib
import json
import re
import stat
import math
from pathlib import Path
from types import SimpleNamespace
import numpy as np
import torch

CONFIG={'task_name':'long_term_forecast','seq_len':60,'label_len':30,'pred_len':5,'enc_in':6,'dec_in':6,'c_out':1,'d_model':32,'n_heads':4,'e_layers':1,'d_layers':1,'d_ff':64,'dropout':.1,'factor':3,'embed':'timeF','freq':'d','activation':'gelu'}
ID_PATTERN=r'transformer-s3-2023-seed(42|43|44)(?:-[a-f0-9]{16})?'
INSTRUMENTS=['SH600030','SH600276','SZ000002']
FEATURES=['open','high','low','close','vwap','volume']
SOURCE='baostock-backward-close-over-raw-v1'
FEATURE='baostock-six-price-volume-v1'
TARGET='adjusted-close-relative-origin-v1'
COMMIT='4e938a1767106324dd753b2a44832bf870a0252e'
class InferenceError(Exception):
    def __init__(self,code):self.code=code;super().__init__(code)

def digest_file(path):
    with Path(path).open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()

def runtime_hashes():
    root=Path(__file__).parent
    names=['artifacts.py','contracts.py','engine.py','vendor/model.py','vendor/embedding.py','vendor/attention.py','vendor/encoder_decoder.py','vendor/masking.py','vendor/LICENSE','vendor/provenance.json']
    return {name:digest_file(root/name) for name in names}

def scaling(value,positive=False):
    if not isinstance(value,list) or len(value)!=6 or any(type(v) not in (int,float) or abs(v)>1e20 or not math.isfinite(v) or positive and v<1e-6 for v in value):raise InferenceError('MODEL_MANIFEST_INVALID')
    return np.asarray(value,dtype=np.float32)

class ArtifactStore:
    def __init__(self,root):self.root=Path(root).resolve()
    def bounded(self,folder,name,limit):
        path=folder/name
        try:
            if path.is_symlink() or not path.resolve().is_relative_to(self.root) or not stat.S_ISREG(path.stat().st_mode) or not 0<path.stat().st_size<=limit:raise InferenceError('MODEL_ARTIFACT_INVALID')
        except OSError:raise InferenceError('MODEL_ARTIFACT_NOT_FOUND') from None
        return path
    def load(self,artifact_id,manifest_hash):
        if not isinstance(artifact_id,str) or not re.fullmatch(ID_PATTERN,artifact_id) or not isinstance(manifest_hash,str) or not re.fullmatch('[a-f0-9]{64}',manifest_hash):raise InferenceError('MODEL_REQUEST_INVALID')
        folder=self.root/artifact_id
        if folder.is_symlink():raise InferenceError('MODEL_ARTIFACT_INVALID')
        path=self.bounded(folder,'manifest.json',131072)
        if digest_file(path)!=manifest_hash:raise InferenceError('MODEL_MANIFEST_HASH_MISMATCH')
        try:data=json.loads(path.read_text(encoding='utf-8'),parse_constant=lambda _:(_ for _ in ()).throw(ValueError('non-finite')))
        except (ValueError,OSError):raise InferenceError('MODEL_MANIFEST_INVALID') from None
        keys={'schemaVersion','artifactId','status','sourceCommit','checkpointHash','checkpointBytes','config','mean','std','trainingCutoff','epoch','instruments','featureDefinition','sourceDefinition','targetDefinition','features','clip','runtimeHashes'}
        if not isinstance(data,dict) or set(data)!=keys or data['schemaVersion']!=1 or data['artifactId']!=artifact_id or data['status']!='CANDIDATE' or data['sourceCommit']!=COMMIT or data['config']!=CONFIG or data['instruments']!=INSTRUMENTS or data['featureDefinition']!=FEATURE or data['sourceDefinition']!=SOURCE or data['targetDefinition']!=TARGET or data['features']!=FEATURES or data['clip']!=10 or data['trainingCutoff']!='2023-12-31' or type(data['epoch'])!=int or not 1<=data['epoch']<=100 or data['runtimeHashes']!=runtime_hashes():raise InferenceError('MODEL_MANIFEST_INVALID')
        mean,std=scaling(data['mean']),scaling(data['std'],True)
        path=self.bounded(folder,'checkpoint.pt',8388608)
        if data['checkpointBytes']!=path.stat().st_size or digest_file(path)!=data['checkpointHash']:raise InferenceError('MODEL_CHECKPOINT_HASH_MISMATCH')
        try:
            ck=torch.load(path,map_location='cpu',weights_only=True)
            if not isinstance(ck,dict) or set(ck)!=set(('state_dict','config','mean','std','mode','cutoff','epoch')) or ck['config']!=CONFIG or ck['mean']!=data['mean'] or ck['std']!=data['std'] or ck['mode']!='absolute' or ck['cutoff']!=data['trainingCutoff'] or ck['epoch']!=data['epoch']:raise InferenceError('MODEL_CHECKPOINT_INVALID')
            from .vendor.model import Model
            model=Model(SimpleNamespace(**CONFIG));model.load_state_dict(ck['state_dict'],strict=True)
            if sum(p.numel() for p in model.parameters())!=22881 or any(not torch.isfinite(p).all() for p in model.state_dict().values()):raise InferenceError('MODEL_CHECKPOINT_INVALID')
            model.eval()
        except InferenceError:raise
        except Exception:raise InferenceError('MODEL_CHECKPOINT_INVALID') from None
        return data,model,mean,std
