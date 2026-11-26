from fastapi.testclient import TestClient
from app.main import app


def test_health_does_not_claim_inference(monkeypatch):
    monkeypatch.delenv("PREDICTION_MODEL_AUTHORITY_URL", raising=False)
    response = TestClient(app).get("/health")
    assert response.status_code == 200
    assert response.json()["inference"] == "UNAVAILABLE"


def test_inference_is_explicitly_unavailable(monkeypatch):
    monkeypatch.delenv("PREDICTION_MODEL_AUTHORITY_URL", raising=False)
    monkeypatch.setenv("SERVICE_TOKEN", "test-token")
    response = TestClient(app).post("/internal/v1/infer", json={}, headers={"X-Service-Token": "test-token"})
    assert response.status_code == 503
    assert response.json()["detail"] == "PUBLISHED_MODEL_NOT_CONFIGURED"


def test_inference_requires_service_auth(monkeypatch):
    monkeypatch.setenv("SERVICE_TOKEN", "test-token")
    assert TestClient(app).post("/internal/v1/infer").status_code == 401


def test_missing_auth_configuration_fails_closed(monkeypatch):
    monkeypatch.delenv("SERVICE_TOKEN", raising=False)
    response = TestClient(app).post("/internal/v1/infer")
    assert response.status_code == 503
    assert response.json()["detail"] == "INTERNAL_AUTH_NOT_CONFIGURED"


def test_configured_health_requires_publication_check(monkeypatch):
    monkeypatch.setenv("PREDICTION_MODEL_AUTHORITY_URL", "http://stock-prediction-service:8080")
    response = TestClient(app).get("/health")
    assert response.status_code == 200
    assert response.json()["inference"] == "PUBLICATION_CHECK_REQUIRED"
