# User Personas

The system has both human users (operators, engineers) and machine users (upstream payment systems, downstream alerting tools). Each section describes who they are, what they do with the system, and what they need from it.

---

## 1. Payment Platform Engineer (Producer)

**Who.** A backend engineer on the payment processing team who needs to forward transaction events for fraud scoring without blocking the payment path.

**Goals.**
- Fire transactions at the ingest endpoint asynchronously and forget.
- Trust that the API contract (Transaction JSON) is stable.
- Get a fast `202 Accepted` so payment latency is unaffected.

**Touchpoints.**
- `POST /api/transactions` (see [api-reference.md](../api/api-reference.md))
- The `Transaction` schema in [data-models.md](../architecture/data-models.md)

**Pain points addressed.** No need to learn Kafka client libraries — HTTP is the contract.

---

## 2. Fraud Operations Analyst (Consumer of alerts)

**Who.** Someone responsible for triaging suspected fraud in real time.

**Goals.**
- Receive a webhook hit (Slack channel, internal triage queue, paging tool) the moment a HIGH-risk transaction is scored.
- Have enough data in the alert payload to investigate without re-querying the source system.
- Inspect historical flagged events on demand.

**Touchpoints.**
- The webhook configured via `alerts.webhook.url` — receives the full `ScoredTransaction` JSON including `score`, `triggeredRules`, and the original `Transaction`.
- Direct SQL against `flagged_transactions` for back-fill / pattern hunting.

**Pain points addressed.** A single channel for high-confidence alerts; rule names in the payload tell them *why* without a dashboard click.

---

## 3. Fraud Domain Expert (Rule author)

**Who.** Someone who knows what fraudulent behavior looks like and wants to encode it.

**Goals.**
- Add a new rule without rewriting the engine.
- Configure thresholds without redeploying.
- Trust that a misbehaving rule won't crash the whole pipeline.

**Touchpoints.**
- The `Rule` interface ([scoring/rules/Rule.java](../../src/main/java/com/vishavbangotra/fraud_detection_system/scoring/rules/Rule.java)) — three methods: `name()`, `weight()`, `evaluate()`.
- `application.yml` under `fraud.rules.*` for tunables.
- Reference implementations: `AmountThresholdRule` (stateless), `VelocityRule` (Redis ZSET), `GeoMismatchRule` (Redis HASH), `NewDeviceRule` (Redis SET), `MlScoringRule` (HTTP sidecar).

**Pain points addressed.** `RuleEngine` swallows rule exceptions and logs them — one bad rule never breaks scoring for others.

---

## 4. Site Reliability Engineer (Operator)

**Who.** Whoever runs the service in dev / staging / (eventually) prod.

**Goals.**
- Bring the stack up with one command.
- Verify health without grepping logs.
- Reset state quickly when iterating.
- Eventually: dashboards, alerts, on-call rotation.

**Touchpoints.**
- [docker-compose.yaml](../../docker-compose.yaml) for the broker, Redis, Postgres, Kafka UI, and optional ML sidecar.
- Spring Boot Actuator endpoints (`/actuator/health`, `/actuator/info`).
- Kafka UI at http://localhost:8085.
- `DELETE /api/transactions/reset` for local resets.

**Gaps.** No Prometheus metrics export yet. No alerting on consumer lag. Both on the roadmap.

---

## 5. The System Itself (Internal consumers)

The Kafka consumer groups are first-class users of the topics:

| Group | Reads | Writes |
|---|---|---|
| `fraud-detection-streams` (Streams app) | `transactions.raw` | `transactions.scored`, `transactions.flagged` |
| `audit-writer` | `transactions.scored` | `transaction_events` (Postgres) |
| `flagged-writer` | `transactions.flagged` | `flagged_transactions` (Postgres) + webhook |

These can be reset, replayed, or scaled independently — see [low-level-architecture.md](../architecture/low-level-architecture.md) and ADR-004 in [decisions.md](../architecture/decisions.md).
