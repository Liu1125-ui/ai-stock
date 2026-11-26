"""Candidate orchestration; no business database writes."""
import re

def run(operation_id, provider, instrument_id, start, end):
    if not re.fullmatch(r'[A-Za-z0-9_-]{1,128}', operation_id or ''):
        return dict(operationId=operation_id,state='SOURCE_INVALID',candidate=None,errorCode='SOURCE_INVALID',retryAt=None)
    result=provider.collect(instrument_id,start,end)
    candidate=result['candidate']
    if candidate is not None:
        candidate['operationId']=operation_id
    return {'operationId':operation_id,**result}
