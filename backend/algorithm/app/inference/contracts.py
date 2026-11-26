"""The frozen MS-02 window to six-dimensional model input contract."""
import hashlib
import json
import re
from datetime import date,timedelta
from decimal import Decimal,InvalidOperation
import numpy as np
from .artifacts import InferenceError,SOURCE

def number(value,precision,scale):
    # Reject exponent and oversized text before Decimal, float, or hashing.
    if not isinstance(value,str) or len(value)>precision+2 or not re.fullmatch(r'[0-9]+(?:\.[0-9]+)?',value):raise InferenceError('MODEL_INPUT_INVALID')
    try:n=Decimal(value)
    except InvalidOperation:raise InferenceError('MODEL_INPUT_INVALID') from None
    digits=n.as_tuple();integer=len(digits.digits)+digits.exponent
    if not n.is_finite() or n<=0 or -digits.exponent>scale or integer>precision-scale:raise InferenceError('MODEL_INPUT_INVALID')
    return n

def day(value):
    if not isinstance(value,str) or not re.fullmatch(r'\d{4}-\d{2}-\d{2}',value):raise InferenceError('MODEL_INPUT_INVALID')
    try:result=date.fromisoformat(value)
    except ValueError:raise InferenceError('MODEL_INPUT_INVALID') from None
    if not date(1990,1,1)<=result<=date(2099,12,31):raise InferenceError('MODEL_INPUT_INVALID')
    return result

def prepare_window(window,instruments):
    if not isinstance(window,dict) or window.get('instrumentId') not in instruments or window.get('provider')!='baostock' or window.get('priceBasis')!='RAW' or window.get('definition')!=SOURCE or window.get('freshness') not in ('FRESH','STALE','UNKNOWN'):raise InferenceError('MODEL_INPUT_INCOMPATIBLE')
    for key in ('inputVersionId','rawVersionId'):
        if not isinstance(window.get(key),str) or not re.fullmatch(r'[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}',window[key]):raise InferenceError('MODEL_INPUT_INVALID')
    for key in ('rawContentHash','inputHash','calendarHash'):
        if not isinstance(window.get(key),str) or not re.fullmatch('[a-f0-9]{64}',window[key]):raise InferenceError('MODEL_INPUT_INVALID')
    rows=window.get('bars');targets=window.get('targetDates')
    if not isinstance(rows,list) or len(rows)!=60 or not isinstance(targets,list) or len(targets)!=5:raise InferenceError('MODEL_INPUT_INVALID')
    previous=None;values=[]
    for row in rows:
        if not isinstance(row,dict):raise InferenceError('MODEL_INPUT_INVALID')
        current=day(row.get('date'))
        if previous is not None and current<=previous:raise InferenceError('MODEL_INPUT_INVALID')
        o,h,l,c=[number(row.get(k),20,6) for k in ('open','high','low','close')]
        v=number(row.get('volumeShares'),20,4);a=number(row.get('amountYuan'),24,6);f=number(row.get('sourceAdjustmentRatio'),24,12)
        if h<max(o,l,c) or l>min(o,h,c) or not l-Decimal('.01')<=a/v<=h+Decimal('.01'):raise InferenceError('MODEL_INPUT_INVALID')
        # Float64 operations match research pandas preprocessing, then float32 input.
        of,hf,lf,cf,vf,af,ff=map(float,(o,h,l,c,v,a,f))
        values.append([of*ff,hf*ff,lf*ff,cf*ff,af/vf*ff,vf/ff]);previous=current
    if day(window.get('originDate'))!=previous or number(window.get('originRawClose'),20,6)!=number(rows[-1]['close'],20,6):raise InferenceError('MODEL_INPUT_INVALID')
    origin=previous
    for target in targets:
        current=day(target)
        if current<=previous or current>origin+timedelta(days=90):raise InferenceError('MODEL_INPUT_INVALID')
        previous=current
    base=np.asarray(values,dtype=np.float32)
    if base.shape!=(60,6) or not np.isfinite(base).all() or not (base>0).all():raise InferenceError('MODEL_INPUT_INVALID')
    return base,Decimal(window['originRawClose'])

def input_digest(tensor,targets):
    return hashlib.sha256(tensor.tobytes(order='C')+json.dumps(targets,separators=(',',':')).encode()).hexdigest()
