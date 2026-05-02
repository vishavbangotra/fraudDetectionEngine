# Product Documentation

## What is Fraud Detection System?

A self-hosted, real-time scoring service for payment transactions. It accepts a stream of transaction events over HTTP, evaluates each one against a set of configurable rules — some stateless (amount), some stateful per customer (velocity, geo, device) — and produces a risk-classified output stream. High-risk events are persisted separately and fanned out to webhooks for downstream action.

## What problem does it solve?

A typical payment system has dozens of fraud signals (high-value transactions, rapid bursts, sudden country switches, unfamiliar devices). Embedding these signals directly into the payment path couples them to release cadence and adds latency. This service decouples scoring: payments emit events, scoring happens asynchronously, and any operational team can wire rules and alerts without touching payment code.

## Core capabilities

- **HTTP ingestion** of single transaction events (`POST /api/transactions`).
- **Synthetic load generator** for local testing (`POST /api/transactions/simulate?count=N`).
- **Rules engine** with four built-in rules (Amount, Velocity, Geo, NewDevice) plus an optional ML-backed rule, each independently weighted; new rules drop in as a `@Component implements Rule`.
- **Risk classification** into `LOW` / `MEDIUM` / `HIGH` from a 0–100 score.
- **Audit stream** — every scored event is persisted to `transaction_events`, regardless of risk.
- **Flagged stream** — HIGH-risk events are persisted to `flagged_transactions` and POSTed to a configurable webhook.
- **Operator reset** (`DELETE /api/transactions/reset`) for clearing DB, Redis, and Kafka topic offsets during development.

## Out-of-the-box rules

| Rule | Weight | Triggers when |
|---|---|---|
| `AMOUNT_THRESHOLD` | 40 | `amount > fraud.rules.amount.threshold` (default 10 000) |
| `VELOCITY` | 25 | More than `fraud.rules.velocity.max` (default 5) txns from the same customer in the last `fraud.rules.velocity.window-seconds` (default 60) |
| `GEO_MISMATCH` | 25 | Same customer, different country, less than 1 hour since the last transaction |
| `NEW_DEVICE` | 10 | `deviceId` not seen for this customer in the last 90 days |
| `ML_SCORING` | 35 | Optional; Python Isolation Forest sidecar returns `riskScore >= fraud.rules.ml.threshold` (default 0.75) |

A score ≥ 70 is HIGH and triggers an alert. The rule weights and risk-band thresholds are deliberately tunable in code and configuration; see [data-models.md](../architecture/data-models.md) and [low-level-architecture.md](../architecture/low-level-architecture.md).

## What it is not (yet)

- **Not a production-trained ML system.** The sidecar ships a deterministic demo Isolation Forest scorer; production training and calibration remain out of scope.
- **Not a dashboard.** Persisted data is queried by direct SQL today; visualization is on the roadmap.
- **Not multi-tenant.** Single customer namespace; no tenant isolation.
- **Not auth'd.** All endpoints, including the destructive `DELETE /reset`, are open. Local-dev only at present.
- **Not deployable.** No production manifests, no migration tool. See `decisions.md` ADR-008.

## How a transaction flows through the system

1. Caller POSTs a `Transaction` JSON.
2. Controller validates and produces to `transactions.raw` (Kafka), keyed by `customerId`.
3. Kafka Streams reads it, runs all enabled rules, optionally calls the ML sidecar, and emits a `ScoredTransaction` to `transactions.scored`.
4. If `riskLevel == HIGH`, also emits to `transactions.flagged`.
5. `AuditConsumer` writes every scored event to Postgres (`transaction_events`).
6. `FlaggedConsumer` writes HIGH events to `flagged_transactions` and POSTs the event to the configured webhook.

End-to-end latency at idle Kafka load is sub-second on a developer laptop.

## Operational model

- **Deployment:** local Docker Compose for the broker, Redis, Postgres, optional ML sidecar, and Kafka UI; the Spring Boot app runs on the host (`./mvnw spring-boot:run`).
- **Observability:** Spring Boot Actuator endpoints under `/actuator/*`; structured logs via Lombok `@Slf4j`. No metrics export yet — see roadmap.
- **Tuning:** rule weights and thresholds in `application.yml` under `fraud.rules.*`; webhook URL via `ALERT_WEBHOOK_URL` env or `alerts.webhook.url`.
