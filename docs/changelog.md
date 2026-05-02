# Changelog

Format loosely based on [Keep a Changelog](https://keepachangelog.com/). Versions are [TBD] — the project is pre-release; sections are commit-anchored for now.

---

## Unreleased

### Added
- `DELETE /api/transactions/reset` endpoint that clears Postgres tables, Redis (`FLUSHDB`), and truncates the three Kafka topics via `AdminClient.deleteRecords`. Local-dev only — see [decisions.md ADR-011](architecture/decisions.md).

### Documentation
- New `docs/` tree: architecture, product, api, dev, changelog.

---

## 14619be — Add rule engine, consumers, persistence, alerts

The MVP build. Lifts the project from "skeleton with a single threshold filter" to "functional Layers 1–3."

### Added
- **Rule engine** ([scoring/RuleEngine.java](../src/main/java/com/vishavbangotra/fraud_detection_system/scoring/RuleEngine.java)) that aggregates `List<Rule>` injected by Spring; sums weights, caps at 100, maps to `RiskLevel`, swallows per-rule exceptions.
- **Four rules** under [scoring/rules/](../src/main/java/com/vishavbangotra/fraud_detection_system/scoring/rules/):
  - `AmountThresholdRule` (weight 40, stateless)
  - `VelocityRule` (weight 25, Redis ZSET, 5 txns / 60 s default)
  - `GeoMismatchRule` (weight 25, Redis HASH, 1-hour impossible-travel window)
  - `NewDeviceRule` (weight 10, Redis SET, 90-day TTL)
- **`ScoredTransaction` DTO + serdes** so the topology can emit a richer payload than the raw `Transaction`.
- **Three named topics** in [`KafkaConfig`](../src/main/java/com/vishavbangotra/fraud_detection_system/config/KafkaConfig.java): `transactions.raw`, `transactions.scored`, `transactions.flagged` (replaces the old `transactions` / `suspicious-transactions` pair).
- **Audit + flagged consumers** ([consumers/](../src/main/java/com/vishavbangotra/fraud_detection_system/consumers/)) writing to two Postgres tables; separate consumer groups (`audit-writer`, `flagged-writer`) — see ADR-004.
- **Postgres persistence** with two entities (`TransactionEventEntity`, `FlaggedTransactionEntity`) and JpaRepositories.
- **Webhook alerting** ([alerting/WebhookAlertService.java](../src/main/java/com/vishavbangotra/fraud_detection_system/alerting/WebhookAlertService.java)) using Spring `RestClient` with 3 retries × 500 ms backoff; blank URL → no-op; failures swallow rather than rethrow (ADR-006).
- **Real ingest endpoint** `POST /api/transactions` accepting validated `Transaction` JSON; richer simulator at `POST /api/transactions/simulate?count=N` randomizing across customers / merchants / countries / devices.
- **Postgres service** in [docker-compose.yaml](../docker-compose.yaml).
- **Shared `JsonMapper`** with `JavaTimeModule` registered so `Instant` round-trips through Kafka serdes (ADR-009).
- **19 tests** covering `RiskLevel`, `RuleEngine`, `AmountThresholdRule`, the topology routing, and `WebhookAlertService` no-op / retry behavior.

### Changed
- `Transaction` model expanded from `(transactionId, amount, timestamp)` to 11 fields including `customerId`, `merchantId`, `country`, `city`, `latitude`, `longitude`, `deviceId`, `ipAddress`; `timestamp` now `Instant`. Added Jakarta validation annotations.
- `FraudDetectionStream` rewritten: was a hard-coded amount filter; is now a routing topology over the rule engine.
- `application.yml` extended with `spring.datasource.*`, `spring.data.redis.*`, `fraud.rules.*`, `alerts.webhook.url`.

### Removed
- Old topic names `transactions` and `suspicious-transactions` (replaced by the new triple).
- The placeholder `FraudDetectionSystemApplicationTests.contextLoads` smoke test (couldn't load context without Postgres + Redis; targeted unit tests provide better signal).

### Dependencies added
- `spring-boot-starter-data-redis`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-validation`
- `org.postgresql:postgresql` (runtime)
- `kafka-streams-test-utils` (test)

### Architectural decisions captured
ADRs 001–012 in [decisions.md](architecture/decisions.md).

---

## 39d02ee — Add Kafka configuration, transaction serialization, and fraud detection stream

### Added
- `KafkaConfig` declaring `transactions` and `suspicious-transactions` topics (3 partitions, 1 replica each).
- `Transaction` model (`transactionId`, `amount`, `timestamp` — minimal).
- `TransactionSerde` / `TransactionSerializer` / `TransactionDeserializer` (vanilla Jackson `ObjectMapper`).
- `FraudDetectionStream` Kafka Streams topology filtering `amount > 10_000` to `suspicious-transactions`.
- Kafka Streams configuration in `application.yml` (`application-id: fraud-detection-streams`).

---

## e55ef0c — Kafka producer created

### Added
- `TransactionController` with `POST /api/transactions/send` generating 10 random transactions.
- Kafka producer configuration (string key serializer, JSON value serializer, `acks: all`).
- Initial `docker-compose.yaml` with Kafka (KRaft mode), Redis, and Kafka UI.

---

## da551b5 — Initial commit

### Added
- Spring Boot 3.5.9 / Java 21 project scaffolding.
- `pom.xml` with `spring-boot-starter-web`, `actuator`, `spring-kafka`, `kafka-streams`, Lombok, springdoc-openapi.
- Empty `FraudDetectionSystemApplication`.
