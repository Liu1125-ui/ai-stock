import asyncio,json,subprocess,time
import httpx
import pytest
from fastapi.testclient import TestClient
from app.main import app

REQUEST={'artifactId':'transformer-s3-2023-seed42','manifestHash':'a'*64,'window':{}}
HEADERS={'X-Service-Token':'private-test'}

def test_candidate_route_authenticates_before_malformed_json(monkeypatch):
    monkeypatch.setenv('SERVICE_TOKEN','private-test')
    assert TestClient(app).post('/internal/v1/model-artifacts/validate',content='{').status_code==401

def test_candidate_request_size_and_shape_are_bounded(monkeypatch):
    monkeypatch.setenv('SERVICE_TOKEN','private-test');client=TestClient(app)
    assert client.post('/internal/v1/model-artifacts/validate',content='{',headers=HEADERS).status_code==422
    assert client.post('/internal/v1/model-artifacts/validate',content=b'x'*1048577,headers=HEADERS).status_code==413
    assert client.post('/internal/v1/model-artifacts/validate',json={**REQUEST,'artifactId':'../bad'},headers=HEADERS).status_code==422

def test_unconfigured_and_timeout_are_controlled_not_points(monkeypatch):
    from app.inference import http
    monkeypatch.setenv('SERVICE_TOKEN','private-test');monkeypatch.delenv('MODEL_ARTIFACT_ROOT',raising=False)
    r=TestClient(app).post('/internal/v1/model-artifacts/validate',json=REQUEST,headers=HEADERS)
    assert r.json()['state']=='MODEL_ARTIFACT_NOT_CONFIGURED' and r.json()['result'] is None
    monkeypatch.setenv('MODEL_ARTIFACT_ROOT','/models')
    def timeout(*a,**k):raise subprocess.TimeoutExpired('worker',45)
    monkeypatch.setattr(subprocess,'run',timeout)
    r=TestClient(app).post('/internal/v1/model-artifacts/validate',json=REQUEST,headers=HEADERS)
    assert r.json()['state']=='MODEL_VALIDATION_TIMEOUT' and r.json()['result'] is None
    assert http._INFERENCE_SLOT.acquire(blocking=False)
    try:assert TestClient(app).post('/internal/v1/model-artifacts/validate',json=REQUEST,headers=HEADERS).json()['state']=='MODEL_VALIDATION_BUSY'
    finally:http._INFERENCE_SLOT.release()

def test_worker_failure_or_malformed_success_cannot_be_success(monkeypatch):
    monkeypatch.setenv('SERVICE_TOKEN','private-test');monkeypatch.setenv('MODEL_ARTIFACT_ROOT','/models')
    client=TestClient(app)
    for stdout in ('garbage','AISTOCK_MODEL:{"state":"AVAILABLE","result":{"validationOnly":false,"points":[]}}\n'):
        monkeypatch.setattr(subprocess,'run',lambda *a,**k:subprocess.CompletedProcess('worker',0,stdout,''))
        r=client.post('/internal/v1/model-artifacts/validate',json=REQUEST,headers=HEADERS)
        assert r.json()['state']=='MODEL_WORKER_INVALID' and r.json()['result'] is None

def test_slow_model_worker_keeps_health_responsive(monkeypatch):
    monkeypatch.setenv('SERVICE_TOKEN','private-test');monkeypatch.setenv('MODEL_ARTIFACT_ROOT','/models')
    def slow(*a,**k):
        time.sleep(1)
        return subprocess.CompletedProcess('worker',0,'AISTOCK_MODEL:{"state":"MODEL_INPUT_INVALID","result":null}\n','')
    monkeypatch.setattr(subprocess,'run',slow)
    async def run():
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app),base_url='http://test') as client:
            start=time.monotonic();task=asyncio.create_task(client.post('/internal/v1/model-artifacts/validate',json=REQUEST,headers=HEADERS))
            await asyncio.sleep(.02);r=await client.get('/health');elapsed=time.monotonic()-start
            result=await task;assert result.json()['state']=='MODEL_INPUT_INVALID';assert r.status_code==200 and elapsed<.5
    asyncio.run(run())

def test_candidate_validation_does_not_publish_model(monkeypatch):
    monkeypatch.delenv('PREDICTION_MODEL_AUTHORITY_URL',raising=False)
    monkeypatch.setenv('SERVICE_TOKEN','private-test');monkeypatch.setenv('MODEL_ARTIFACT_ROOT','/models')
    client=TestClient(app)
    assert client.get('/health').json()['candidateValidation']=='CONFIGURED'
    r=client.post('/internal/v1/infer',headers=HEADERS)
    assert r.status_code==503 and r.json()['detail']=='PUBLISHED_MODEL_NOT_CONFIGURED'


def test_deeply_nested_json_is_controlled_before_worker(monkeypatch):
    from app.inference import http
    monkeypatch.setenv('SERVICE_TOKEN','private-test')
    def forbidden_worker(body):
        raise AssertionError('Rejected JSON must not start a worker')
    monkeypatch.setattr(http,'run_worker',forbidden_worker)
    body='{"artifactId":"transformer-s3-2023-seed42","manifestHash":"'+('a'*64)+'","window":{"extra":'+('['*10000)+'0'+(']'*10000)+'}}'
    assert len(body.encode())<1048576
    client=TestClient(app)
    response=client.post('/internal/v1/model-artifacts/validate',content=body,headers=HEADERS)
    assert response.status_code==422 and response.json()=={'detail':'MODEL_REQUEST_INVALID'}
    assert client.get('/health').status_code==200


def test_inspection_is_authenticated_and_has_no_prediction_window(monkeypatch):
    monkeypatch.setenv('SERVICE_TOKEN','private-test')
    client=TestClient(app)
    assert client.post('/internal/v1/model-artifacts/inspect',content='{').status_code==401
    assert client.post('/internal/v1/model-artifacts/inspect',json=REQUEST,headers=HEADERS).status_code==422


def test_inspection_safely_loads_actual_fixture_and_never_publishes(monkeypatch,tmp_path):
    monkeypatch.delenv('PREDICTION_MODEL_AUTHORITY_URL',raising=False)
    from test_model_artifacts import bundle,ARTIFACT,manifest_hash
    root,folder,data=bundle.__wrapped__(tmp_path)
    monkeypatch.setenv('SERVICE_TOKEN','private-test');monkeypatch.setenv('MODEL_ARTIFACT_ROOT',str(root))
    client=TestClient(app);body={'artifactId':ARTIFACT,'manifestHash':manifest_hash(folder)}
    response=client.post('/internal/v1/model-artifacts/inspect',json=body,headers=HEADERS)
    assert response.status_code==200 and response.json()['state']=='AVAILABLE'
    result=response.json()['result']
    assert result['inspectionOnly'] is True and result['manifest']==data and 'points' not in result
    assert client.post('/internal/v1/infer',headers=HEADERS).status_code==503
    (folder/'checkpoint.pt').write_bytes(b'corrupt')
    response=client.post('/internal/v1/model-artifacts/inspect',json=body,headers=HEADERS)
    assert response.json()['state']=='MODEL_CHECKPOINT_HASH_MISMATCH' and response.json()['result'] is None


def mock_available_worker(monkeypatch, result):
    monkeypatch.setenv('SERVICE_TOKEN', 'private-test')
    monkeypatch.setenv('MODEL_ARTIFACT_ROOT', '/models')

    def run(*args, **kwargs):
        output = json.dumps({'state': 'AVAILABLE', 'result': result})
        return subprocess.CompletedProcess('worker', 0, 'AISTOCK_MODEL:' + output + '\n', '')

    monkeypatch.setattr(subprocess, 'run', run)


@pytest.mark.parametrize(('field', 'bad_value'), [
    ('formalInference', False),
    ('modelId', '55555555-5555-5555-5555-555555555555'),
    ('publicationRevision', True),
    ('freshness', 'STALE'),
    ('points', []),
    ('step', True),
    ('targetDate', '2026-11-20'),
    ('ratio', 'NaN'),
    ('referencePrice', '0'),
])
def test_formal_response_rejects_inconsistent_worker_output(monkeypatch, field, bad_value):
    request = {
        'artifactId': REQUEST['artifactId'], 'manifestHash': REQUEST['manifestHash'],
        'modelId': '33333333-3333-3333-3333-333333333333',
        'instrumentId': 'SH600030', 'inputVersionId': '44444444-4444-4444-4444-444444444444',
    }
    monkeypatch.setenv('PREDICTION_MODEL_AUTHORITY_URL', 'http://127.0.0.1:1')
    monkeypatch.setenv('MARKET_INPUT_AUTHORITY_URL', 'http://127.0.0.1:1')
    monkeypatch.setenv('MARKET_VERSION_USE_REQUIRED', 'false')
    dates = ('2026-11-23', '2026-11-24', '2026-11-25', '2026-11-26', '2026-11-27')
    result = {
        **request, 'formalInference': True, 'validationOnly': False, 'publicationRevision': 1,
        'freshness': 'FRESH', 'originDate': '2026-11-20',
        'points': [
            {'step': step, 'targetDate': day, 'ratio': '1.01', 'referencePrice': '43.200000'}
            for step, day in enumerate(dates, 1)
        ],
    }
    mock_available_worker(monkeypatch, result)
    client = TestClient(app)
    assert client.post('/internal/v1/infer', json=request, headers=HEADERS).json()['state'] == 'AVAILABLE'

    target = result['points'][0] if field in ('step', 'targetDate', 'ratio', 'referencePrice') else result
    target[field] = bad_value
    response = client.post('/internal/v1/infer', json=request, headers=HEADERS)
    assert response.status_code == 200
    assert response.json() == {'state': 'MODEL_WORKER_INVALID', 'result': None}


@pytest.mark.parametrize(('field', 'bad_value'), [
    ('status', 'PUBLISHED'),
    ('checkpointHash', 'b' * 64),
    ('artifactId', 'another-artifact'),
])
def test_inspection_rejects_manifest_not_matching_verified_artifact(monkeypatch, field, bad_value):
    request = {key: REQUEST[key] for key in ('artifactId', 'manifestHash')}
    result = {
        **request, 'checkpointHash': 'c' * 64, 'inspectionOnly': True,
        'manifest': {'artifactId': request['artifactId'], 'status': 'CANDIDATE', 'checkpointHash': 'c' * 64},
    }
    mock_available_worker(monkeypatch, result)
    client = TestClient(app)
    assert client.post('/internal/v1/model-artifacts/inspect', json=request, headers=HEADERS).json()['state'] == 'AVAILABLE'

    result['manifest'][field] = bad_value
    response = client.post('/internal/v1/model-artifacts/inspect', json=request, headers=HEADERS)
    assert response.status_code == 200
    assert response.json() == {'state': 'MODEL_WORKER_INVALID', 'result': None}


@pytest.mark.parametrize('mode', ['inspect', 'validate', 'invalid-manifest-hash'])
def test_worker_entrypoint_preserves_candidate_boundary(monkeypatch, tmp_path, capsys, mode):
    import io
    import sys
    from app.inference import worker
    from test_model_artifacts import ARTIFACT, bundle, manifest_hash, window

    root, folder, manifest = bundle.__wrapped__(tmp_path)
    request = {'artifactId': ARTIFACT, 'manifestHash': manifest_hash(folder)}
    if mode == 'validate':
        request['window'] = window.__wrapped__()
    elif mode == 'invalid-manifest-hash':
        request['manifestHash'] = '0' * 64

    monkeypatch.setenv('MODEL_ARTIFACT_ROOT', str(root))
    monkeypatch.setattr(sys, 'stdin', io.StringIO(json.dumps(request)))
    worker.main()
    prefix, payload = capsys.readouterr().out.strip().split(':', 1)
    assert prefix == 'AISTOCK_MODEL'
    receipt = json.loads(payload)

    if mode == 'invalid-manifest-hash':
        assert receipt == {'state': 'MODEL_MANIFEST_HASH_MISMATCH', 'result': None}
    else:
        assert receipt['state'] == 'AVAILABLE'
        assert receipt['result']['checkpointHash'] == manifest['checkpointHash']
        if mode == 'inspect':
            assert receipt['result']['inspectionOnly'] is True
            assert 'points' not in receipt['result']
        else:
            assert receipt['result']['validationOnly'] is True
            assert [point['targetDate'] for point in receipt['result']['points']] == request['window']['targetDates']
