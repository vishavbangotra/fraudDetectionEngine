from __future__ import annotations

import hashlib
import math
import os
from datetime import datetime, timezone
from typing import Optional

import numpy as np
from fastapi import FastAPI
from pydantic import BaseModel, Field
from sklearn.ensemble import IsolationForest

MODEL_NAME = "isolation_forest"
MODEL_VERSION = "iforest-demo-v1"
ANOMALY_THRESHOLD = float(os.getenv("ML_ANOMALY_THRESHOLD", "0.75"))


class Transaction(BaseModel):
    transactionId: Optional[str] = None
    customerId: Optional[str] = None
    merchantId: Optional[str] = None
    amount: Optional[float] = None
    country: Optional[str] = None
    city: Optional[str] = None
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    deviceId: Optional[str] = None
    ipAddress: Optional[str] = None
    timestamp: Optional[datetime] = None


class ScoreResponse(BaseModel):
    model: str
    modelVersion: str
    riskScore: float = Field(ge=0.0, le=1.0)
    anomaly: bool
    reasonCodes: list[str]


def _hash_feature(value: Optional[str]) -> float:
    if not value:
        return 0.0
    digest = hashlib.sha256(value.encode("utf-8")).hexdigest()
    return int(digest[:8], 16) / 0xFFFFFFFF


def _timestamp_parts(ts: Optional[datetime]) -> tuple[float, float, float]:
    if ts is None:
        ts = datetime.now(timezone.utc)
    if ts.tzinfo is None:
        ts = ts.replace(tzinfo=timezone.utc)
    ts = ts.astimezone(timezone.utc)
    hour = ts.hour + ts.minute / 60.0
    radians = 2.0 * math.pi * hour / 24.0
    weekend = 1.0 if ts.weekday() >= 5 else 0.0
    return math.sin(radians), math.cos(radians), weekend


def _feature_vector(txn: Transaction) -> list[float]:
    amount = max(txn.amount or 0.0, 0.0)
    hour_sin, hour_cos, weekend = _timestamp_parts(txn.timestamp)
    has_location = 1.0 if txn.latitude is not None and txn.longitude is not None else 0.0
    has_ip = 1.0 if txn.ipAddress else 0.0

    return [
        min(math.log1p(amount) / 12.0, 1.5),
        hour_sin,
        hour_cos,
        weekend,
        has_location,
        has_ip,
        _hash_feature(txn.country),
        _hash_feature(txn.merchantId),
        _hash_feature(txn.deviceId),
    ]


def _training_matrix() -> np.ndarray:
    rng = np.random.default_rng(42)
    countries = np.array(["US", "GB", "CA", "IN", "DE"])
    merchants = np.array(["merch-amazon", "merch-target", "merch-uber", "merch-apple"])
    devices = np.array(["dev-iphone-1", "dev-android-1", "dev-web-1", "dev-tablet-1"])
    rows: list[list[float]] = []

    for i in range(1200):
        amount = float(np.clip(rng.lognormal(mean=4.6, sigma=0.65), 2.0, 6000.0))
        hour = float(np.clip(rng.normal(loc=13.0, scale=4.0), 0.0, 23.75))
        ts = datetime(2026, 5, 1, int(hour), int((hour % 1) * 60), tzinfo=timezone.utc)
        rows.append(_feature_vector(Transaction(
            transactionId=f"train-{i}",
            customerId=f"cust-{i % 25}",
            merchantId=str(rng.choice(merchants)),
            amount=amount,
            country=str(rng.choice(countries)),
            latitude=40.7128,
            longitude=-74.0060,
            deviceId=str(rng.choice(devices)),
            ipAddress=f"10.0.0.{i % 255}",
            timestamp=ts,
        )))

    return np.array(rows, dtype=float)


TRAINING_DATA = _training_matrix()
MODEL = IsolationForest(
    n_estimators=100,
    contamination=0.04,
    random_state=42,
)
MODEL.fit(TRAINING_DATA)
TRAINING_SCORES = MODEL.decision_function(TRAINING_DATA)
SCORE_LOW = float(np.quantile(TRAINING_SCORES, 0.02))
SCORE_HIGH = float(np.quantile(TRAINING_SCORES, 0.95))

app = FastAPI(title="Fraud ML Sidecar", version=MODEL_VERSION)


@app.get("/health")
def health() -> dict[str, str]:
    return {
        "status": "ok",
        "model": MODEL_NAME,
        "modelVersion": MODEL_VERSION,
    }


@app.post("/score", response_model=ScoreResponse)
def score(txn: Transaction) -> ScoreResponse:
    features = np.array([_feature_vector(txn)], dtype=float)
    decision_score = float(MODEL.decision_function(features)[0])
    denominator = max(SCORE_HIGH - SCORE_LOW, 1e-9)
    risk_score = 1.0 - ((decision_score - SCORE_LOW) / denominator)
    risk_score = round(float(np.clip(risk_score, 0.0, 1.0)), 4)

    return ScoreResponse(
        model=MODEL_NAME,
        modelVersion=MODEL_VERSION,
        riskScore=risk_score,
        anomaly=risk_score >= ANOMALY_THRESHOLD,
        reasonCodes=_reason_codes(txn),
    )


def _reason_codes(txn: Transaction) -> list[str]:
    codes: list[str] = []
    if (txn.amount or 0.0) >= 10000.0:
        codes.append("high_amount")
    hour_sin, hour_cos, _ = _timestamp_parts(txn.timestamp)
    hour = (math.atan2(hour_sin, hour_cos) * 24.0 / (2.0 * math.pi)) % 24.0
    if hour < 5.0 or hour >= 23.0:
        codes.append("off_hours")
    if txn.latitude is None or txn.longitude is None:
        codes.append("location_missing")
    if not txn.ipAddress:
        codes.append("ip_missing")
    return codes
