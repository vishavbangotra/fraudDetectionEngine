from fastapi.testclient import TestClient

from app import app

client = TestClient(app)


def normal_transaction() -> dict:
    return {
        "transactionId": "txn-normal",
        "customerId": "cust-1",
        "merchantId": "merch-amazon",
        "amount": 125.50,
        "country": "US",
        "city": "New York",
        "latitude": 40.7128,
        "longitude": -74.006,
        "deviceId": "dev-iphone-1",
        "ipAddress": "10.0.0.1",
        "timestamp": "2026-05-01T14:30:00Z",
    }


def test_health_reports_ready_model():
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "ok",
        "model": "isolation_forest",
        "modelVersion": "iforest-demo-v1",
    }


def test_score_returns_stable_response_shape():
    response = client.post("/score", json=normal_transaction())

    assert response.status_code == 200
    body = response.json()
    assert body["model"] == "isolation_forest"
    assert body["modelVersion"] == "iforest-demo-v1"
    assert 0.0 <= body["riskScore"] <= 1.0
    assert isinstance(body["anomaly"], bool)
    assert isinstance(body["reasonCodes"], list)


def test_high_anomaly_scores_above_default_threshold():
    payload = normal_transaction()
    payload.update({
        "transactionId": "txn-anomaly",
        "amount": 50000.0,
        "latitude": None,
        "longitude": None,
        "ipAddress": None,
        "timestamp": "2026-05-01T02:15:00Z",
    })

    response = client.post("/score", json=payload)

    assert response.status_code == 200
    body = response.json()
    assert body["riskScore"] >= 0.75
    assert body["anomaly"] is True
    assert "high_amount" in body["reasonCodes"]
