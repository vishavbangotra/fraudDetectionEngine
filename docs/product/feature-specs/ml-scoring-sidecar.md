# Feature: ML Scoring Sidecar

## 1. Summary

Add optional model-based fraud scoring as `ML_SCORING`, a normal `Rule` implementation that calls a Python FastAPI sidecar. The first bundled model is a deterministic demo Isolation Forest; existing Kafka topics, database tables, and `ScoredTransaction` shape do not change.

## 2. Motivation

Fraud domain experts need a way to detect unusual combinations of signals that are awkward to encode as hand-written rules. The existing rule engine already provides the extension point, so ML scoring can be introduced without changing the stream topology.

## 3. Goals & Non-Goals

**Goals.**
- Enable ML scoring with `fraud.rules.ml.enabled=true`.
- Fail open when the sidecar is unavailable so non-ML scoring continues.
- Keep Python ML dependencies outside the Spring Boot process.

**Non-goals.**
- Production model training, registry, drift detection, or labeled fraud calibration.
- New Kafka topics, database columns, or synchronous scoring APIs.

## 4. Proposed Design

`MlScoringRule` is conditionally registered as a Spring `Rule`. It posts the existing `Transaction` JSON to the sidecar `POST /score`, checks the returned `riskScore` against `fraud.rules.ml.threshold`, and contributes `fraud.rules.ml.weight` points when it fires.

The sidecar exposes `GET /health` and `POST /score`. Its response includes `model`, `modelVersion`, normalized `riskScore`, `anomaly`, and coarse `reasonCodes`. Only the rule name and weighted score contribution flow into the Java service's existing output.

## 5. Configuration

| Key | Default | Meaning |
|---|---|---|
| `fraud.rules.ml.enabled` | `false` | Register `ML_SCORING` |
| `fraud.rules.ml.url` | `http://localhost:8090/score` | Sidecar endpoint |
| `fraud.rules.ml.threshold` | `0.75` | Minimum sidecar risk score to fire |
| `fraud.rules.ml.weight` | `35` | Points added when fired |
| `fraud.rules.ml.connect-timeout-ms` | `250` | HTTP connect timeout |
| `fraud.rules.ml.read-timeout-ms` | `500` | HTTP read timeout |

## 6. Test Plan

- Java unit tests for rule thresholding, fail-open behavior, response parsing, non-2xx responses, and invalid risk scores.
- Python pytest suite for sidecar health, response shape, and obvious anomaly scoring.
- Manual e2e: start `docker compose --profile ml up -d ml-sidecar`, run the Spring app with `--fraud.rules.ml.enabled=true`, post a high-risk transaction, and confirm `ML_SCORING` appears in `triggeredRules`.

## 7. Risks & Mitigations

| Risk | Mitigation |
|---|---|
| Sidecar outage hides ML-only fraud | Fail open is logged; non-ML rules continue. Add metrics/DLQ later. |
| Demo model is mistaken for production fraud intelligence | Docs label it as deterministic demo scoring only. |
| HTTP call adds stream latency | Short connect/read timeouts and disabled-by-default rollout. |
