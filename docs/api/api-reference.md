# API Reference

Base URL (local dev): `http://localhost:8080`
Interactive UI: http://localhost:8080/swagger-ui.html
OpenAPI JSON: http://localhost:8080/v3/api-docs

All endpoints are unauthenticated at present. Do not expose this service to a network you don't fully trust.

---

## `POST /api/transactions`

Submit a single transaction for fraud scoring.

**Source.** [`TransactionController.ingest`](../../src/main/java/com/vishavbangotra/fraud_detection_system/controller/TransactionController.java)

### Request

`Content-Type: application/json`

Body — see [Transaction model](../architecture/data-models.md#transaction-input):

```json
{
  "transactionId": "txn-001",
  "customerId":    "cust-1",
  "merchantId":    "merch-amazon",
  "amount":        12500.00,
  "country":       "US",
  "city":          "New York",
  "latitude":      40.7128,
  "longitude":     -74.0060,
  "deviceId":      "dev-iphone-1",
  "ipAddress":     "192.168.10.42",
  "timestamp":     "2026-05-01T12:34:56Z"
}
```

**Required:** `transactionId`, `customerId`, `merchantId`, `amount` (>0), `country`, `deviceId`, `timestamp`.
**Optional:** `city`, `latitude`, `longitude`, `ipAddress`.

### Responses

| Status | Body | Meaning |
|---|---|---|
| `202 Accepted` | `transactionId` | Produced to `transactions.raw`; scoring happens asynchronously |
| `400 Bad Request` | Spring validation error JSON | Missing or invalid field |

### Example

```bash
curl -X POST http://localhost:8080/api/transactions \
  -H 'Content-Type: application/json' \
  -d '{
    "transactionId":"txn-001","customerId":"cust-1","merchantId":"merch-amazon",
    "amount":12500,"country":"US","deviceId":"dev-iphone-1",
    "timestamp":"2026-05-01T12:34:56Z"
  }'
```

### Side effects

- Produces one record to `transactions.raw` (key = `customerId`).
- Triggers downstream scoring; no synchronous result is returned. Inspect `transaction_events` (Postgres) or `transactions.scored` (Kafka) for the outcome.

---

## `POST /api/transactions/simulate`

Generate a batch of synthetic transactions and produce them to `transactions.raw`. Used for local testing — randomizes across 5 customers, 4 merchants, 5 countries, and 4 devices so velocity / geo / device rules can fire.

**Source.** [`TransactionController.simulate`](../../src/main/java/com/vishavbangotra/fraud_detection_system/controller/TransactionController.java)

### Request

| Param | In | Type | Default | Notes |
|---|---|---|---|---|
| `count` | query | int | `10` | Number of transactions to generate |

No request body.

### Response

| Status | Body |
|---|---|
| `202 Accepted` | `Sent <count> transactions` |

### Example

```bash
curl -X POST 'http://localhost:8080/api/transactions/simulate?count=50'
```

---

## `POST /api/transactions/send`  *(deprecated)*

Backwards-compatible alias for `simulate(10)`. Marked `@Deprecated`. Prefer `/simulate?count=10`.

**Source.** [`TransactionController.sendLegacy`](../../src/main/java/com/vishavbangotra/fraud_detection_system/controller/TransactionController.java)

---

## `DELETE /api/transactions/reset`

**Local development only.** Clears all persisted state across the three sinks:

1. `deleteAllInBatch()` on `transaction_events` and `flagged_transactions` (one transaction).
2. Redis `FLUSHDB`.
3. For each of `transactions.raw`, `transactions.scored`, `transactions.flagged`: `AdminClient.deleteRecords(beforeOffset = latest)` to truncate.

**Source.** [`TransactionController.reset`](../../src/main/java/com/vishavbangotra/fraud_detection_system/controller/TransactionController.java)

### Responses

| Status | Body | Meaning |
|---|---|---|
| `200 OK` | `DB, Redis, and Kafka topics cleared` | Everything cleared |
| `500 Internal Server Error` | `DB/Redis cleared but Kafka reset failed: <reason>` | DB + Redis succeeded, Kafka truncation failed |

### Example

```bash
curl -X DELETE http://localhost:8080/api/transactions/reset
```

---

## Actuator endpoints

Provided by `spring-boot-starter-actuator`. Default exposed set per Boot 3.5 — at minimum:

- `GET /actuator/health` — liveness/readiness composite
- `GET /actuator/info` — build / app info

Enable additional endpoints (`metrics`, `prometheus`, etc.) under `management.endpoints.web.exposure.include` when needed.

---

## OpenAPI / Swagger

- `springdoc-openapi` 2.0.2 is wired in.
- Spec: `GET /v3/api-docs`
- UI: `GET /swagger-ui.html`

Both are enabled in `application.yml` under `springdoc.*`.

---

## ML sidecar API

Local base URL when running `docker compose --profile ml up -d ml-sidecar`: `http://localhost:8090`.

### `GET /health`

Returns sidecar readiness and the loaded model identity:

```json
{
  "status": "ok",
  "model": "isolation_forest",
  "modelVersion": "iforest-demo-v1"
}
```

### `POST /score`

Used by `MlScoringRule` when `fraud.rules.ml.enabled=true`. The request body is the same `Transaction` JSON shape accepted by `POST /api/transactions`.

Response:

```json
{
  "model": "isolation_forest",
  "modelVersion": "iforest-demo-v1",
  "riskScore": 0.82,
  "anomaly": true,
  "reasonCodes": ["high_amount", "off_hours"]
}
```

The Java rule fires when `riskScore >= fraud.rules.ml.threshold`; sidecar errors fail open and do not block normal scoring.

---

## Asynchronous outputs (not HTTP, but part of the API surface)

| Topic | Format | Consumers expected |
|---|---|---|
| `transactions.scored` | `ScoredTransaction` JSON ([model](../architecture/data-models.md#scoredtransaction-output)) | Anything wanting an audit stream |
| `transactions.flagged` | `ScoredTransaction` JSON | Triage / alerting |

| Outbound | Trigger | Body |
|---|---|---|
| Webhook POST → `${alerts.webhook.url}` | One per HIGH-risk event | `ScoredTransaction` JSON |
