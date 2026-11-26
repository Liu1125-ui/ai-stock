import os
import secrets
import threading
_COLLECT_SLOT = threading.BoundedSemaphore(1)
from fastapi import FastAPI, HTTPException, Header

from app.market.contracts import CollectRequest

app = FastAPI(title="AI Stock Algorithm", version="0.1.0")

from app.training.api import router as training_router
app.include_router(training_router)


@app.get("/health")
def health():
    return {"marketUseProtocol":"market-version-use-v1","marketUseRequired":os.environ.get("MARKET_VERSION_USE_REQUIRED","false").strip().lower()=="true","status": "UP", "inference": "PUBLICATION_CHECK_REQUIRED" if os.environ.get("PREDICTION_MODEL_AUTHORITY_URL", "") else "UNAVAILABLE", "candidateValidation": "CONFIGURED" if os.environ.get("MODEL_ARTIFACT_ROOT", "") else "NOT_CONFIGURED"}


@app.post("/internal/v1/market/collect")
def collect_market(body: "CollectRequest", x_service_token: str | None = Header(default=None)):
    import json
    import subprocess
    import sys
    from app.market.contracts import CollectRequest
    configured = os.environ.get("SERVICE_TOKEN", "")
    if not configured:
        raise HTTPException(status_code=503, detail="INTERNAL_AUTH_NOT_CONFIGURED")
    if not secrets.compare_digest((x_service_token or "").encode(), configured.encode()):
        raise HTTPException(status_code=401, detail="INTERNAL_AUTH_REQUIRED")
    if not _COLLECT_SLOT.acquire(blocking=False):
        return {"operationId":body.operationId,"state":"SOURCE_BUSY","candidate":None,"errorCode":"SOURCE_BUSY","retryAt":None}
    try:
        output = subprocess.run([sys.executable, "-m", "app.market.worker"],
            input=json.dumps(body.model_dump()), text=True, capture_output=True, timeout=25, check=False)
        if output.returncode != 0:
            raise ValueError("worker failed")
        matches = [line for line in output.stdout.splitlines() if line.startswith("AISTOCK_CANDIDATE:")]
        if len(matches) != 1:
            raise ValueError("worker result missing")
        return json.loads(matches[0].split(":", 1)[1])
    except subprocess.TimeoutExpired:
        return {"operationId":body.operationId,"state":"SOURCE_TIMEOUT","candidate":None,"errorCode":"SOURCE_TIMEOUT","retryAt":None}
    except (ValueError, OSError):
        return {"operationId":body.operationId,"state":"SOURCE_INVALID","candidate":None,"errorCode":"SOURCE_INVALID","retryAt":None}
    finally:
        _COLLECT_SLOT.release()

from fastapi import Request

@app.post("/internal/v1/market/forecast-collect")
async def collect_forecast(request: Request, x_service_token: str | None = Header(default=None)):
    import json
    import subprocess
    import sys
    from app.forecast.provider import validate_request, ForecastSource
    configured=os.environ.get('SERVICE_TOKEN','')
    if not configured:raise HTTPException(status_code=503,detail='INTERNAL_AUTH_NOT_CONFIGURED')
    if not secrets.compare_digest((x_service_token or '').encode(),configured.encode()):
        raise HTTPException(status_code=401,detail='INTERNAL_AUTH_REQUIRED')
    limit=2097152
    content=bytearray()
    async for chunk in request.stream():
        content.extend(chunk)
        if len(content)>limit:raise HTTPException(status_code=413,detail='MARKET_PAYLOAD_TOO_LARGE')
    try:
        body=json.loads(content);validate_request(body)
    except (ValueError,TypeError,KeyError,AttributeError):
        raise HTTPException(status_code=422,detail='FORECAST_REQUEST_INVALID') from None
    from starlette.concurrency import run_in_threadpool
    return await run_in_threadpool(_run_forecast_worker,body)


def _run_forecast_worker(body, module="app.forecast.worker", marker="AISTOCK_FORECAST:"):
    import json
    import subprocess
    import sys
    from app.forecast.provider import ForecastSource
    # The worker thread owns the slot until the child exits, including client cancellation.
    if not _COLLECT_SLOT.acquire(blocking=False):return ForecastSource.error('SOURCE_BUSY')
    try:
        output=subprocess.run([sys.executable,'-m',module],input=json.dumps(body),
            text=True,capture_output=True,timeout=25,check=False)
        lines=[s.split(':',1)[1] for s in output.stdout.splitlines() if s.startswith(marker)]
        if output.returncode!=0 or len(lines)!=1:raise ValueError('Missing worker result')
        result=json.loads(lines[0])
        if not isinstance(result,dict) or result.get('state') not in ('AVAILABLE','SOURCE_BUSY','SOURCE_TIMEOUT','SOURCE_UNAVAILABLE','SOURCE_INVALID'):
            raise ValueError('Invalid worker result')
        if result['state']=='AVAILABLE' and not isinstance(result.get('candidate'),dict):raise ValueError('Missing candidate')
        if result['state']!='AVAILABLE' and result.get('candidate') is not None:raise ValueError('Failed result contains candidate')
        return result
    except subprocess.TimeoutExpired:return ForecastSource.error('SOURCE_TIMEOUT')
    except (ValueError,OSError):return ForecastSource.error('SOURCE_INVALID')
    finally:_COLLECT_SLOT.release()

from app.inference.http import router as inference_validation_router
app.include_router(inference_validation_router)

@app.post('/internal/v1/market/calendar-collect')
async def collect_calendar(request: Request, x_service_token: str | None = Header(default=None)):
    import json
    from app.calendar.provider import validate_request
    from starlette.concurrency import run_in_threadpool
    configured = os.environ.get('SERVICE_TOKEN', '')
    if not configured:
        raise HTTPException(status_code=503, detail='INTERNAL_AUTH_NOT_CONFIGURED')
    if not secrets.compare_digest((x_service_token or '').encode(), configured.encode()):
        raise HTTPException(status_code=401, detail='INTERNAL_AUTH_REQUIRED')
    content = bytearray()
    async for chunk in request.stream():
        content.extend(chunk)
        if len(content) > 4096:
            raise HTTPException(status_code=413, detail='CALENDAR_PAYLOAD_TOO_LARGE')
    try:
        body = json.loads(content)
        validate_request(body)
    except (ValueError, TypeError, KeyError):
        raise HTTPException(status_code=422, detail='CALENDAR_REQUEST_INVALID') from None
    return await run_in_threadpool(_run_calendar_worker, body)


def _run_calendar_worker(body):
    import json
    import subprocess
    import sys
    from app.calendar.provider import CalendarSource
    if not _COLLECT_SLOT.acquire(blocking=False):
        return CalendarSource.error(body['operationId'], 'SOURCE_BUSY')
    try:
        output = subprocess.run([sys.executable, '-m', 'app.calendar.worker'], input=json.dumps(body),
            text=True, capture_output=True, timeout=25, check=False)
        lines = [s.split(':', 1)[1] for s in output.stdout.splitlines() if s.startswith('AISTOCK_CALENDAR:')]
        if output.returncode != 0 or len(lines) != 1:
            raise ValueError('Invalid worker result')
        result = json.loads(lines[0])
        if not isinstance(result, dict) or result.get('operationId') != body['operationId'] or result.get('state') not in ('AVAILABLE','SOURCE_BUSY','SOURCE_TIMEOUT','SOURCE_UNAVAILABLE','SOURCE_INVALID'):
            raise ValueError('Invalid worker result')
        if (result['state'] == 'AVAILABLE') != isinstance(result.get('candidate'), dict):
            raise ValueError('Invalid worker candidate')
        return result
    except subprocess.TimeoutExpired:
        return CalendarSource.error(body['operationId'], 'SOURCE_TIMEOUT')
    except (ValueError, OSError):
        return CalendarSource.error(body['operationId'], 'SOURCE_INVALID')
    finally:
        _COLLECT_SLOT.release()

@app.post('/internal/v1/market/alert-collect')
async def collect_alert(request: Request, x_service_token: str | None = Header(default=None)):
    import json
    from app.alert.provider import validate_request
    from starlette.concurrency import run_in_threadpool
    configured=os.environ.get('SERVICE_TOKEN','')
    if not configured:raise HTTPException(status_code=503,detail='INTERNAL_AUTH_NOT_CONFIGURED')
    if not secrets.compare_digest((x_service_token or '').encode(),configured.encode()):
        raise HTTPException(status_code=401,detail='INTERNAL_AUTH_REQUIRED')
    content=bytearray()
    async for chunk in request.stream():
        content.extend(chunk)
        if len(content)>4096:raise HTTPException(status_code=413,detail='ALERT_PAYLOAD_TOO_LARGE')
    try:
        body=json.loads(content);validate_request(body)
    except (ValueError,TypeError,KeyError,AttributeError):
        raise HTTPException(status_code=422,detail='ALERT_REQUEST_INVALID') from None
    return await run_in_threadpool(_run_forecast_worker,body,'app.alert.worker','AISTOCK_ALERT:')

@app.post("/internal/v1/market/comparison-collect")
async def collect_comparison(request: Request, x_service_token: str | None = Header(default=None)):
    import json
    from app.comparison.provider import validate_request
    from starlette.concurrency import run_in_threadpool
    configured = os.environ.get("SERVICE_TOKEN", "")
    if not configured:
        raise HTTPException(status_code=503, detail="INTERNAL_AUTH_NOT_CONFIGURED")
    if not secrets.compare_digest((x_service_token or "").encode(), configured.encode()):
        raise HTTPException(status_code=401, detail="INTERNAL_AUTH_REQUIRED")
    content = bytearray()
    async for chunk in request.stream():
        content.extend(chunk)
        if len(content)>4096:
            raise HTTPException(status_code=413, detail="COMPARISON_PAYLOAD_TOO_LARGE")
    try:
        body = json.loads(content)
        validate_request(body)
    except (ValueError,TypeError,KeyError,AttributeError):
        raise HTTPException(status_code=422, detail="COMPARISON_REQUEST_INVALID") from None
    return await run_in_threadpool(_run_comparison_worker, body)


def _run_comparison_worker(body):
    import json
    import subprocess
    import sys
    from app.comparison.provider import ComparisonSource
    if not _COLLECT_SLOT.acquire(blocking=False):
        return ComparisonSource.error("SOURCE_BUSY")
    try:
        from app.comparison.process import run_bounded_worker
        stdout = run_bounded_worker(body)
        lines = [s.split(":",1)[1] for s in stdout.splitlines() if s.startswith("AISTOCK_COMPARISON:")]
        if len(lines)!=1:
            raise ValueError("Missing worker result")
        result = json.loads(lines[0])
        states = ("AVAILABLE","SOURCE_BUSY","SOURCE_TIMEOUT","SOURCE_UNAVAILABLE","SOURCE_INVALID")
        if not isinstance(result,dict) or result.get("state") not in states:
            raise ValueError("Invalid state")
        if (result["state"]=="AVAILABLE") != isinstance(result.get("candidate"),dict):
            raise ValueError("Invalid candidate")
        return result
    except subprocess.TimeoutExpired:
        return ComparisonSource.error("SOURCE_TIMEOUT")
    except (ValueError,OSError):
        return ComparisonSource.error("SOURCE_INVALID")
    finally:
        _COLLECT_SLOT.release()
@app.post("/internal/v1/market/index-collect")
async def collect_indices(request: Request, x_service_token: str | None = Header(default=None)):
    import json
    from app.market.index_source import validate_request
    from starlette.concurrency import run_in_threadpool

    configured = os.environ.get("SERVICE_TOKEN", "")
    if not configured:
        raise HTTPException(status_code=503, detail="INTERNAL_AUTH_NOT_CONFIGURED")
    if not secrets.compare_digest((x_service_token or "").encode(), configured.encode()):
        raise HTTPException(status_code=401, detail="INTERNAL_AUTH_REQUIRED")
    content = bytearray()
    async for chunk in request.stream():
        content.extend(chunk)
        if len(content) > 4096:
            raise HTTPException(status_code=413, detail="INDEX_PAYLOAD_TOO_LARGE")
    try:
        body = json.loads(content)
        validate_request(body)
    except (ValueError, TypeError):
        raise HTTPException(status_code=422, detail="INDEX_REQUEST_INVALID") from None
    return await run_in_threadpool(_run_forecast_worker, body, "app.market.index_worker", "AISTOCK_INDEX:")
