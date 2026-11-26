"""Canonical business usage headers for formal inference only."""
import os,re
from uuid import UUID

KEYS=('X-Market-Use-Bundle','X-Market-Use-Owner','X-Market-Use-Purpose','X-Market-Use-Operation','X-Market-Use-Generation')

def validate_usage(value,required=None):
    if required is None:required=os.environ.get('MARKET_VERSION_USE_REQUIRED','false').strip().lower()=='true'
    if not value:
        if required:raise ValueError('market usage required')
        return {}
    if not isinstance(value,dict) or set(value)!=set(KEYS) or any(not isinstance(v,str) for v in value.values()):raise ValueError('invalid usage')
    for key in (KEYS[0],KEYS[3]):
        if str(UUID(value[key]))!=value[key]:raise ValueError('invalid usage id')
    if value[KEYS[1]]!='prediction' or value[KEYS[2]]!='FORECAST' or not re.fullmatch(r'[1-9][0-9]{0,18}',value[KEYS[4]]) or int(value[KEYS[4]])>9223372036854775807:raise ValueError('invalid usage identity')
    return {key:value[key] for key in KEYS}

def request_usage(headers):
    present=[key for key in headers.keys() if key.lower().startswith('x-market-use-')]
    if any(key.lower() not in {k.lower() for k in KEYS} for key in present):raise ValueError('unknown usage header')
    value={}
    for key in KEYS:
        values=headers.getlist(key)
        if len(values)>1:raise ValueError('duplicate usage header')
        if values:value[key]=values[0]
    return validate_usage(value)
