"""Authenticated candidate inspection/validation; no model publication."""
import json,os,re,secrets,subprocess,sys,threading
from uuid import UUID
from datetime import date
from decimal import Decimal
from fastapi import APIRouter,Header,HTTPException,Request
from starlette.concurrency import run_in_threadpool
from .pool_artifacts import valid_artifact_id
router=APIRouter();_INFERENCE_SLOT=threading.BoundedSemaphore(1)
ERRORS={'MODEL_MARKET_USE_INVALID','MODEL_NOT_PUBLISHED','MODEL_PUBLICATION_UNAVAILABLE','MODEL_PUBLICATION_MISMATCH','MODEL_INPUT_NOT_CURRENT','MODEL_INPUT_UNAVAILABLE','MODEL_ARTIFACT_NOT_CONFIGURED','MODEL_ARTIFACT_INVALID','MODEL_ARTIFACT_NOT_FOUND','MODEL_REQUEST_INVALID','MODEL_MANIFEST_HASH_MISMATCH','MODEL_MANIFEST_INVALID','MODEL_CHECKPOINT_HASH_MISMATCH','MODEL_CHECKPOINT_INVALID','MODEL_INPUT_INVALID','MODEL_INPUT_INCOMPATIBLE','MODEL_OUTPUT_INVALID','MODEL_VALIDATION_FAILED','MODEL_VALIDATION_TIMEOUT','MODEL_VALIDATION_BUSY','MODEL_WORKER_INVALID'}

def failure(code):return {'state':code,'result':None}

def authenticate(token):
    configured=os.environ.get('SERVICE_TOKEN','')
    if not configured:raise HTTPException(status_code=503,detail='INTERNAL_AUTH_NOT_CONFIGURED')
    if not secrets.compare_digest((token or '').encode(),configured.encode()):raise HTTPException(status_code=401,detail='INTERNAL_AUTH_REQUIRED')

async def read_request(request,token,keys):
    authenticate(token)
    content=bytearray()
    async for chunk in request.stream():
        content.extend(chunk)
        if len(content)>1048576:raise HTTPException(status_code=413,detail='MODEL_PAYLOAD_TOO_LARGE')
    try:
        body=json.loads(content)
        if not isinstance(body,dict) or set(body)!=keys or not valid_artifact_id(body['artifactId']) or not isinstance(body['manifestHash'],str) or not re.fullmatch('[a-f0-9]{64}',body['manifestHash']) or 'window' in body and not isinstance(body['window'],dict):raise ValueError('invalid request')
    except (ValueError,TypeError,KeyError,RecursionError):raise HTTPException(status_code=422,detail='MODEL_REQUEST_INVALID') from None
    return body

@router.post('/internal/v1/infer')
async def infer_published(request:Request,x_service_token:str|None=Header(default=None)):
    authenticate(x_service_token)
    if not os.environ.get('PREDICTION_MODEL_AUTHORITY_URL','') or not os.environ.get('MARKET_INPUT_AUTHORITY_URL',''):
        raise HTTPException(status_code=503,detail='PUBLISHED_MODEL_NOT_CONFIGURED')
    body=await read_request(request,x_service_token,{'modelId','artifactId','manifestHash','instrumentId','inputVersionId'})
    try:
        if any(not isinstance(body[key],str) or str(UUID(body[key]))!=body[key] for key in ('modelId','inputVersionId')) or not isinstance(body['instrumentId'],str) or not re.fullmatch(r'(SH|SZ)[0-9]{6}',body['instrumentId']):raise ValueError()
    except (ValueError,TypeError,AttributeError):raise HTTPException(status_code=422,detail='MODEL_REQUEST_INVALID') from None
    from .market_use import request_usage
    try:usage=request_usage(request.headers)
    except (ValueError,TypeError,AttributeError):raise HTTPException(status_code=422,detail='MODEL_MARKET_USE_INVALID') from None
    if usage:body['_marketUse']=usage
    return await run_in_threadpool(run_worker,body)

@router.post('/internal/v1/model-artifacts/validate')
async def validate_candidate(request:Request,x_service_token:str|None=Header(default=None)):
    body=await read_request(request,x_service_token,{'artifactId','manifestHash','window'})
    return await run_in_threadpool(run_worker,body)

@router.post('/internal/v1/model-artifacts/inspect')
async def inspect_candidate(request:Request,x_service_token:str|None=Header(default=None)):
    body=await read_request(request,x_service_token,{'artifactId','manifestHash'})
    return await run_in_threadpool(run_worker,body)

def run_worker(body):
    if not os.environ.get('MODEL_ARTIFACT_ROOT',''):return failure('MODEL_ARTIFACT_NOT_CONFIGURED')
    if not _INFERENCE_SLOT.acquire(blocking=False):return failure('MODEL_VALIDATION_BUSY')
    try:
        process=subprocess.run([sys.executable,'-m','app.inference.worker'],input=json.dumps(body,allow_nan=False),text=True,capture_output=True,timeout=45,check=False)
        lines=[line.split(':',1)[1] for line in process.stdout.splitlines() if line.startswith('AISTOCK_MODEL:')]
        if process.returncode!=0 or len(lines)!=1:raise ValueError('worker failed')
        output=json.loads(lines[0]);state=output.get('state') if isinstance(output,dict) else None
        if state=='AVAILABLE':
            result=output.get('result')
            if not isinstance(result,dict) or result.get('artifactId')!=body['artifactId'] or result.get('manifestHash')!=body['manifestHash']:raise ValueError('invalid identity')
            if 'modelId' in body:
                if (result.get('formalInference') is not True or result.get('validationOnly') is not False
                        or any(result.get(key)!=body[key] for key in ('modelId','instrumentId','inputVersionId'))
                        or type(result.get('publicationRevision')) is not int or result['publicationRevision']<1
                        or result.get('freshness')!='FRESH' or not isinstance(result.get('points'),list) or len(result['points'])!=5):raise ValueError('invalid formal identity')
                previous=date.fromisoformat(result['originDate'])
                for step,point in enumerate(result['points'],1):
                    target=date.fromisoformat(point['targetDate'])
                    if type(point.get('step')) is not int or point['step']!=step or target<=previous:raise ValueError('invalid formal dates')
                    for key in ('ratio','referencePrice'):
                        value=point.get(key)
                        if not isinstance(value,str) or len(value)>24 or not re.fullmatch(r'[0-9]+(?:\.[0-9]{1,12})?',value) or Decimal(value)<=0:raise ValueError('invalid formal point')
                    previous=target
            elif 'window' in body:
                window=body['window']
                if result.get('validationOnly') is not True or result.get('inputVersionId')!=window.get('inputVersionId') or result.get('inputHash')!=window.get('inputHash') or not isinstance(result.get('points'),list) or len(result['points'])!=5 or [p.get('targetDate') for p in result['points']]!=window.get('targetDates'):raise ValueError('invalid success')
            else:
                manifest=result.get('manifest')
                if result.get('inspectionOnly') is not True or 'points' in result or not isinstance(manifest,dict) or manifest.get('artifactId')!=body['artifactId'] or manifest.get('status')!='CANDIDATE' or manifest.get('checkpointHash')!=result.get('checkpointHash') or not isinstance(result.get('checkpointHash'),str) or not re.fullmatch('[a-f0-9]{64}',result['checkpointHash']):raise ValueError('invalid inspection')
        elif state not in ERRORS or output.get('result') is not None:raise ValueError('invalid failure')
        return output
    except subprocess.TimeoutExpired:return failure('MODEL_VALIDATION_TIMEOUT')
    except (ValueError,TypeError,KeyError,AttributeError,OSError,RecursionError):return failure('MODEL_WORKER_INVALID')
    finally:_INFERENCE_SLOT.release()
