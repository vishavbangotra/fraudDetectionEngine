# Data Models

## Transaction (input)

[model/Transaction.java](../../src/main/java/com/vishavbangotra/fraud_detection_system/model/Transaction.java) — JSON DTO accepted by the REST ingest and produced to `transactions.raw`.

| Field | Type | Validation | Notes |
|---|---|---|---|
| `transactionId` | `String` | `@NotBlank` | Caller-supplied unique id |
| `customerId` | `String` | `@NotBlank` | Used as Kafka key (per-customer partition affinity) |
| `merchantId` | `String` | `@NotBlank` | |
| `amount` | `Double` | `@NotNull @Positive` | Currency-agnostic at MVP |
| `country` | `String` | `@NotBlank` | ISO-3166 alpha-2 expected (`US`, `GB`, …) |
| `city` | `String` | — | Optional |
| `latitude` | `Double` | — | Optional |
| `longitude` | `Double` | — | Optional |
| `deviceId` | `String` | `@NotBlank` | Drives NewDeviceRule |
| `ipAddress` | `String` | — | Optional, currently informational only |
| `timestamp` | `Instant` | `@NotNull` | Event time; serialized as ISO-8601 |

Lombok: `@Data`, `@Builder`, `@AllArgsConstructor`, `@NoArgsConstructor`.

### Example

```json
{
  "transactionId": "txn-001",
  "customerId": "cust-1",
  "merchantId": "merch-amazon",
  "amount": 12500.00,
  "country": "US",
  "city": "New York",
  "latitude": 40.7128,
  "longitude": -74.0060,
  "deviceId": "dev-iphone-1",
  "ipAddress": "192.168.10.42",
  "timestamp": "2026-05-01T12:34:56Z"
}
```

## ScoredTransaction (output)

[scoring/ScoredTransaction.java](../../src/main/java/com/vishavbangotra/fraud_detection_system/scoring/ScoredTransaction.java) — emitted to `transactions.scored` and (for HIGH) `transactions.flagged`. Also the body POSTed to the alert webhook.

| Field | Type | Notes |
|---|---|---|
| `transaction` | `Transaction` | Original payload, unchanged |
| `score` | `int` | Sum of fired rule weights, capped at 100 |
| `triggeredRules` | `List<String>` | Rule `name()` values, e.g. `["AMOUNT_THRESHOLD","VELOCITY"]` |
| `riskLevel` | `RiskLevel` | `LOW` (<30), `MEDIUM` (30..69), `HIGH` (≥70) |

### Example

```json
{
  "transaction": { "...": "see above" },
  "score": 65,
  "triggeredRules": ["AMOUNT_THRESHOLD", "NEW_DEVICE"],
  "riskLevel": "MEDIUM"
}
```

## ML Sidecar ScoreResponse

Returned by `POST /score` on the optional Python sidecar. This is not persisted directly; `MlScoringRule` uses `riskScore` to decide whether to add `ML_SCORING` to `triggeredRules`.

| Field | Type | Notes |
|---|---|---|
| `model` | `String` | `isolation_forest` for the bundled demo scorer |
| `modelVersion` | `String` | Stable sidecar model identifier, e.g. `iforest-demo-v1` |
| `riskScore` | `double` | Normalized 0.0–1.0 anomaly risk |
| `anomaly` | `boolean` | Sidecar-local threshold result; Java still applies `fraud.rules.ml.threshold` |
| `reasonCodes` | `List<String>` | Coarse explanations such as `high_amount`, `off_hours`, `location_missing` |

## RiskLevel

Enum at [scoring/RiskLevel.java](../../src/main/java/com/vishavbangotra/fraud_detection_system/scoring/RiskLevel.java).

| Value | Score range |
|---|---|
| `LOW` | 0 – 29 |
| `MEDIUM` | 30 – 69 |
| `HIGH` | 70 – 100 |

`RiskLevel.fromScore(int)` is the single source of truth for these bands.

## Database Schema

### `transaction_events` (full audit)

Mapped from [persistence/TransactionEventEntity.java](../../src/main/java/com/vishavbangotra/fraud_detection_system/persistence/TransactionEventEntity.java).

| Column | Type | Constraints |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `transaction_id` | `VARCHAR` | NOT NULL, indexed (`idx_txn_events_txn_id`) |
| `customer_id` | `VARCHAR` | NOT NULL, indexed (`idx_txn_events_customer_id`) |
| `merchant_id` | `VARCHAR` | |
| `amount` | `DOUBLE PRECISION` | NOT NULL |
| `country`, `city` | `VARCHAR` | |
| `latitude`, `longitude` | `DOUBLE PRECISION` | |
| `device_id`, `ip_address` | `VARCHAR` | |
| `transaction_timestamp` | `TIMESTAMP WITH TIME ZONE` | NOT NULL |
| `score` | `INTEGER` | NOT NULL |
| `triggered_rules` | `VARCHAR(512)` | Comma-joined rule names |
| `risk_level` | `VARCHAR(16)` | NOT NULL, enum string (`LOW`/`MEDIUM`/`HIGH`) |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | NOT NULL |

### `flagged_transactions` (HIGH-risk only)

Same shape as `transaction_events` but persisted only for HIGH-risk events. Indexes: `idx_flagged_txn_id`, `idx_flagged_customer_id`. Rows are written by `FlaggedConsumer`; the same record is also written to `transaction_events` by `AuditConsumer` (the two consumers are independent).

## Redis Key Layout

| Key pattern | Type | Purpose |
|---|---|---|
| `velocity:{customerId}` | Sorted set | Members = transactionIds, score = epoch-ms; powers VelocityRule |
| `geo:{customerId}` | Hash | Fields `country`, `lastSeenMillis`; powers GeoMismatchRule |
| `devices:{customerId}` | Set | Known deviceIds; powers NewDeviceRule |

See [low-level-architecture.md](low-level-architecture.md) for TTLs and trigger semantics.

The ML sidecar is stateless at request time; it keeps the demo Isolation Forest model in process memory and does not add Redis keys or database tables.

## Kafka Topic Schemas

| Topic | Key | Value | Producer | Consumer(s) |
|---|---|---|---|---|
| `transactions.raw` | `String` (customerId) | `Transaction` JSON | `TransactionController` | `FraudDetectionStream` |
| `transactions.scored` | `String` (customerId) | `ScoredTransaction` JSON | `FraudDetectionStream` | `AuditConsumer` (group `audit-writer`) |
| `transactions.flagged` | `String` (customerId) | `ScoredTransaction` JSON | `FraudDetectionStream` | `FlaggedConsumer` (group `flagged-writer`) |

All three topics: 3 partitions, 1 replica (local dev). JSON serialization via Jackson with `JavaTimeModule` registered for `Instant` round-trip.
