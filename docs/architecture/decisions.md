# Architectural Decisions

A lightweight ADR log of choices made during the MVP build. Each entry: context, decision, why, and known tradeoffs.

---

## ADR-001: Kafka Streams (DSL) over plain `@KafkaListener` for scoring

**Context.** Two reasonable shapes for the scoring stage: (a) a Kafka Streams topology, (b) a simple `@KafkaListener` consumer that produces back to Kafka.

**Decision.** Use Kafka Streams (`FraudDetectionStream` with `@EnableKafkaStreams`).

**Why.** Streams gives first-class per-partition ordering, exactly-once-style processing semantics, and a topology that can later grow stateful operations (windowed aggregates, joins) without changing the wiring shape. Plain listeners would force us to re-implement those primitives.

**Tradeoffs.** More moving parts (state store directory, application-id) and slightly more conceptual overhead than a `@KafkaListener`. `@KafkaListener` is still used downstream for the audit/flagged sinks, where stream semantics aren't needed.

---

## ADR-002: Per-customer Kafka key (`customerId`)

**Context.** Three Redis-backed rules (Velocity, GeoMismatch, NewDevice) hold state keyed by customer.

**Decision.** All producers key messages by `customerId`.

**Why.** Same-customer events land on the same partition, so the rules read/write the same Redis key from a single stream thread. This avoids cross-thread races on the per-customer state and keeps locality predictable as the topology scales.

**Tradeoffs.** Hot customers (e.g. a high-volume merchant aggregator) become partition hot-spots. Acceptable at MVP scale; revisit if observed.

---

## ADR-003: Redis for stateful rules instead of Kafka Streams state stores

**Context.** Velocity / Geo / NewDevice all need state. Kafka Streams ships with RocksDB-backed state stores.

**Decision.** Use Redis (Lettuce client) directly inside each rule.

**Why.** (1) The state is small per customer and naturally TTL-able — Redis's expiry primitives are a perfect fit. (2) The rules are operational guards, not analytical aggregates; their state is more cache-like than stream-like. (3) An external store can be inspected and reset out-of-band (`flushdb`), which is invaluable for local dev and demos.

**Tradeoffs.** Adds Redis as a hard runtime dependency for the stream thread (a Redis outage stalls scoring). For a production hardening, wrap Redis calls with a circuit breaker so failures degrade to "stateless rules only" rather than blocking the topology.

---

## ADR-004: Two consumer groups (`audit-writer`, `flagged-writer`) over one combined writer

**Context.** Both `transactions.scored` and `transactions.flagged` need to be persisted, and `transactions.flagged` additionally fires alerts.

**Decision.** Two `@KafkaListener` beans in independent consumer groups, reading from independent topics.

**Why.** Each group can fail / lag / be reset without affecting the other. Replaying the audit stream doesn't re-fire alerts. Operational ownership stays clean.

**Tradeoffs.** Every HIGH event is written twice — once to `transaction_events` (by AuditConsumer) and once to `flagged_transactions` (by FlaggedConsumer). At MVP scale this is negligible. If row count becomes a concern, drop the duplicate write and join at query time.

---

## ADR-005: Webhook over email/SMS for the first alert sink

**Context.** README listed webhook + SMTP. Both add value; only one is needed for an MVP.

**Decision.** Webhook only, behind a single configurable URL (`alerts.webhook.url`).

**Why.** Webhooks are trivial to test locally (webhook.site, `nc`, `ngrok`), require no credentials, and integrate with anything (Slack, PagerDuty, internal triage queues). SMTP requires a configured mail server and per-recipient list management — not MVP-worthy.

**Tradeoffs.** No human notification path out of the box. Easy to add later behind a small `AlertSink` interface.

---

## ADR-006: Webhook failures swallow, don't rethrow

**Context.** `WebhookAlertService` is called from inside a `@KafkaListener`. If the alert call throws, Spring Kafka treats the listener as failed and re-delivers the message.

**Decision.** Retry up to 3× with 500 ms backoff inside the service; on exhaustion, log at ERROR and return normally.

**Why.** Re-delivery would cause `flagged_transactions` to be written twice. The Kafka offset commit is the source of truth for "was this flagged event handled" — duplicating writes for the sake of alert retries is the wrong tradeoff. A missing alert is observable in logs and through the persisted `flagged_transactions` row.

**Tradeoffs.** A genuinely down webhook can lose alerts silently. Add a dead-letter queue or out-of-band reconciliation job once volume justifies it.

---

## ADR-007: `triggeredRules` stored as a comma-joined string

**Context.** Could model rules-per-event as a join table.

**Decision.** Single `VARCHAR(512)` column, comma-joined rule names.

**Why.** Read-only audit data, no need to query "which transactions triggered rule X" at MVP scale. A join table would double the write volume and complicate the entity for no current benefit.

**Tradeoffs.** Querying by rule requires `LIKE '%RULE_NAME%'` (slow) or a new column. Migrate to a join table when query patterns demand it.

---

## ADR-008: `ddl-auto: update` for schema management

**Context.** Production-grade options would be Flyway or Liquibase.

**Decision.** Hibernate `ddl-auto: update` for the MVP.

**Why.** Schema is small (2 tables), churn is low, and the team is one person at this stage. Adding a migration tool now would add ceremony without reducing risk.

**Tradeoffs.** Not safe for any deployment that matters. Migrate to Flyway before the system goes anywhere near a real prod environment — flagged in [roadmap.md](../product/roadmap.md).

---

## ADR-009: New JSON-mapper helper to register `JavaTimeModule`

**Context.** Existing `TransactionSerializer/Deserializer` instantiated a vanilla `ObjectMapper` — adding an `Instant timestamp` field broke JSON round-trip.

**Decision.** Centralize via [`serdes/JsonMapper.create()`](../../src/main/java/com/vishavbangotra/fraud_detection_system/serdes/JsonMapper.java) which registers `JavaTimeModule` and disables `WRITE_DATES_AS_TIMESTAMPS`.

**Why.** Single point of truth — both the existing Transaction serdes and the new ScoredTransaction serdes use it.

**Tradeoffs.** Doesn't share Spring's auto-configured `ObjectMapper`. Acceptable: the Kafka serdes are instantiated by Kafka, not Spring, so they can't depend on the Spring context anyway.

---

## ADR-010: Skip Testcontainers; pure unit tests + manual e2e

**Context.** Plan called for Testcontainers Redis/Postgres tests for the rules and repos.

**Decision.** Write Mockito/`TopologyTestDriver` tests only; rely on the documented end-to-end verification (`docker compose up` + curl + psql) for real-infra coverage.

**Why.** (1) Mocking deeply-chained `StringRedisTemplate` calls is brittle and tests little real behavior. (2) Spinning up real containers per test is expensive on each `mvn test`. (3) The e2e procedure is concrete and runnable.

**Tradeoffs.** No CI signal for Redis-key-layout regressions. Add Testcontainers once the project lives in CI — flagged in [roadmap.md](../product/roadmap.md).

---

## ADR-011: `DELETE /api/transactions/reset` for dev only

**Context.** Iterating on rules locally requires clearing DB rows, Redis keys, and Kafka topic offsets.

**Decision.** Expose a single endpoint that flushes all three.

**Why.** Cuts the iteration cycle from "manually run psql + redis-cli + kafka-console-consumer" to one curl.

**Tradeoffs.** Catastrophic if exposed publicly. Must be removed or guarded (Spring Security profile, network ACL) before any non-local deployment. Currently has no auth.

---

## ADR-012: No ML in the MVP

**Context.** Original architecture included Isolation Forest / logistic regression scoring.

**Decision.** Defer.

**Why.** A clean rules engine has demonstrable value, is testable, and forms the contract that an ML score would later plug into (just another `Rule` implementation). Building both at once muddies the design.

**Tradeoffs.** The system can't catch novel patterns the rules don't cover. Roadmap item.
