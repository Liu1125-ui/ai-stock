import json
import subprocess
from fastapi.testclient import TestClient
from app.main import app

def test_collect_requires_internal_token(monkeypatch):
    monkeypatch.setenv('SERVICE_TOKEN','test-token')
    body=dict(operationId='probe-1',provider='baostock',instrumentId='SH600030',start='2026-09-20',end='2026-09-24')
    assert TestClient(app).post('/internal/v1/market/collect',json=body).status_code==401

def test_worker_timeout_is_explicit_failure(monkeypatch):
    monkeypatch.setenv('SERVICE_TOKEN','test-token')
    def timeout(*args,**kwargs):raise subprocess.TimeoutExpired('worker',25)
    monkeypatch.setattr(subprocess,'run',timeout)
    body=dict(operationId='probe-1',provider='baostock',instrumentId='SH600030',start='2026-09-20',end='2026-09-24')
    result=TestClient(app).post('/internal/v1/market/collect',json=body,headers={'X-Service-Token':'test-token'})
    assert result.status_code==200
    assert result.json()['state']=='SOURCE_TIMEOUT'
    assert result.json()['candidate'] is None

def test_collection_capacity_is_bounded(monkeypatch):
    from app import main
    monkeypatch.setenv('SERVICE_TOKEN','test-token')
    body=dict(operationId='probe-1',provider='baostock',instrumentId='SH600030',start='2026-09-20',end='2026-09-24')
    assert main._COLLECT_SLOT.acquire(blocking=False)
    try:
        response=TestClient(app).post('/internal/v1/market/collect',json=body,headers={'X-Service-Token':'test-token'})
        assert response.json()['state']=='SOURCE_BUSY'
    finally:
        main._COLLECT_SLOT.release()
