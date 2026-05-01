# High-Level Architecture

## Purpose

A real-time fraud detection pipeline that ingests payment transactions over HTTP, scores each transaction against a set of rules (some stateless, some Redis-backed and stateful per customer), persists every event for audit, and fires webhook alerts for high-risk activity.

## System Diagram

```
            ┌─────────────────────┐
   HTTP --->│ TransactionController│
            │   POST /api/        │
            │   transactions      │
            └──────────┬──────────┘
                       │ KafkaTemplate.send(...)
                       ▼
              ┌──────────────────┐
              │ transactions.raw │ (Kafka topic, 3 partitions, key=customerId)
              └────────┬─────────┘
                       │
           ┌───────────▼────────────┐         ┌──────────┐
           │ FraudDetectionStream   │ ◄─────► │  Redis   │
           │ (Kafka Streams)        │  rules  │ (state)  │
           │   ↳ RuleEngine.score() │         └──────────┘
           └──────┬─────────┬───────┘
                  │         │
                  ▼         ▼ (filter riskLevel == HIGH)
       ┌────────────────┐  ┌─────────────────────┐
       │transactions.   │  │ transactions.flagged│
       │   scored       │  └──────────┬──────────┘
       └───────┬────────┘             │
               │                      │
               ▼                      ▼
        ┌─────────────┐        ┌──────────────┐
        │AuditConsumer│        │FlaggedConsumer│
        └──────┬──────┘        └──────┬────────┘
               │                      │
               ▼                      ├─► WebhookAlertService ──► (POST)
       ┌─────────────────┐            ▼
       │transaction_     │     ┌────────────────────┐
       │  events (PG)    │     │flagged_transactions│
       └─────────────────┘     │      (PG)          │
                               └────────────────────┘
```

## Components

| Component | Responsibility | Source |
|---|---|---|
| `TransactionController` | REST ingest + simulator | [controller/TransactionController.java](../../src/main/java/com/vishavbangotra/fraud_detection_system/controller/TransactionController.java) |
| `FraudDetectionStream` | Kafka Streams topology: raw → scored → flagged | [streams/FraudDetectionStream.java](../../src/main/java/com/vishavbangotra/fraud_detection_system/streams/FraudDetectionStream.java) |
| `RuleEngine` | Aggregates rule outputs into a `ScoredTransaction` | [scoring/RuleEngine.java](../../src/main/java/com/vishavbangotra/fraud_detection_system/scoring/RuleEngine.java) |
| Rules | Independent scoring units, each `@Component implements Rule` | [scoring/rules/](../../src/main/java/com/vishavbangotra/fraud_detection_system/scoring/rules/) |
| `AuditConsumer` | Persists every scored event | [consumers/AuditConsumer.java](../../src/main/java/com/vishavbangotra/fraud_detection_system/consumers/AuditConsumer.java) |
| `FlaggedConsumer` | Persists HIGH-risk events + fires alert | [consumers/FlaggedConsumer.java](../../src/main/java/com/vishavbangotra/fraud_detection_system/consumers/FlaggedConsumer.java) |
| `WebhookAlertService` | POSTs `ScoredTransaction` JSON, 3 retries × 500 ms | [alerting/WebhookAlertService.java](../../src/main/java/com/vishavbangotra/fraud_detection_system/alerting/WebhookAlertService.java) |

## Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Runtime | Java | 21 |
| Framework | Spring Boot | 3.5.9 |
| Stream processing | Apache Kafka Streams | (Spring Boot managed) |
| Cache / state | Redis | latest (Lettuce client) |
| Database | PostgreSQL | 16 |
| API docs | springdoc-openapi | 2.0.2 |
| Build | Maven (wrapper) | — |
| Containerization | Docker Compose | v3.8 |

## Data Flow

1. Caller sends `POST /api/transactions` with a JSON `Transaction`.
2. Controller validates (Jakarta validation) and produces to `transactions.raw` keyed by `customerId` — co-locating per-customer state on one partition for the Redis-backed rules.
3. `FraudDetectionStream` reads `transactions.raw`, calls `RuleEngine.score(txn)`, and produces a `ScoredTransaction` to `transactions.scored`.
4. The same stream branches: any `ScoredTransaction` with `riskLevel == HIGH` is also produced to `transactions.flagged`.
5. `AuditConsumer` (group `audit-writer`) reads `transactions.scored` → row in `transaction_events`.
6. `FlaggedConsumer` (group `flagged-writer`) reads `transactions.flagged` → row in `flagged_transactions` → `WebhookAlertService.send(...)`.

## Scope

**In scope (current MVP):** ingestion, rules engine (4 rules), audit + flagged persistence, webhook alerts.

**Out of scope (deliberate):** ML scoring (Isolation Forest / logistic regression), Prometheus + Grafana wiring, email alerts, SMS alerts, time-series store, web dashboard.
