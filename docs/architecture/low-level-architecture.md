# Low-Level Architecture

This document describes the internals of each component, the wiring between them, and the runtime contracts.

## Package Layout

Root: `com.vishavbangotra.fraud_detection_system`

```
├── FraudDetectionSystemApplication       (Spring Boot entry point)
├── config/
│   └── KafkaConfig                       (topic NewTopic beans + topic name constants)
├── controller/
│   └── TransactionController             (REST API)
├── streams/
│   └── FraudDetectionStream              (@EnableKafkaStreams + topology bean)
├── scoring/
│   ├── RiskLevel                         (LOW / MEDIUM / HIGH + fromScore)
│   ├── ScoredTransaction                 (DTO emitted to .scored / .flagged)
│   ├── RuleEngine                        (aggregates all Rule beans)
│   └── rules/
│       ├── Rule                          (interface: name(), weight(), evaluate())
│       ├── AmountThresholdRule           (weight 40, stateless)
│       ├── VelocityRule                  (weight 25, Redis ZSET)
│       ├── GeoMismatchRule               (weight 25, Redis HASH)
│       └── NewDeviceRule                 (weight 10, Redis SET)
├── serdes/
│   ├── JsonMapper                        (shared ObjectMapper w/ JavaTimeModule)
│   ├── TransactionSerde / Serializer / Deserializer
│   └── ScoredTransactionSerde / Serializer / Deserializer
├── persistence/
│   ├── TransactionEventEntity            (@Table transaction_events)
│   ├── FlaggedTransactionEntity          (@Table flagged_transactions)
│   ├── TransactionEventRepository        (JpaRepository)
│   └── FlaggedTransactionRepository      (JpaRepository)
├── consumers/
│   ├── ScoredTransactionConsumerConfig   (factory bean for Scored events)
│   ├── AuditConsumer                     (group audit-writer → transactions.scored)
│   └── FlaggedConsumer                   (group flagged-writer → transactions.flagged)
└── alerting/
    └── WebhookAlertService               (RestClient + retry)
```

## Topology Detail

`FraudDetectionStream.fraudDetectionStreamProcessor(StreamsBuilder)`:

```
KStream<String, Transaction> raw
    = builder.stream("transactions.raw",
                     Consumed.with(Serdes.String(), new TransactionSerde()));

KStream<String, ScoredTransaction> scored = raw.mapValues(ruleEngine::score);

scored.to("transactions.scored",
          Produced.with(Serdes.String(), new ScoredTransactionSerde()));

scored.filter((k, v) -> v != null && v.getRiskLevel() == HIGH)
      .peek(...log.warn...)
      .to("transactions.flagged",
          Produced.with(Serdes.String(), new ScoredTransactionSerde()));
```

- Streams `application-id`: `fraud-detection-streams` (consumer group + state-store namespace).
- All Kafka keys are `customerId` so per-customer Redis state and Kafka partition assignment are aligned.
- `ruleEngine` is a Spring bean injected via the `FraudDetectionStream` constructor — `mapValues` captures it in the topology closure.

## Rule Contract

```java
interface Rule {
    String name();          // stable identifier used in ScoredTransaction.triggeredRules
    int weight();           // points added to score when evaluate() == true (uncapped at rule level)
    boolean evaluate(Transaction txn);  // may read/write Redis; must be safe for null sub-fields
}
```

`RuleEngine.score(Transaction)`:
1. For each rule, call `evaluate(txn)`. Wrap in try/catch — a thrown rule is logged and skipped, never aborts scoring.
2. Sum weights of fired rules; cap final score at 100.
3. `RiskLevel.fromScore`: `< 30 → LOW`, `30..69 → MEDIUM`, `≥ 70 → HIGH`.
4. Return `ScoredTransaction { transaction, score, triggeredRules, riskLevel }`.

## Redis Key Layout

| Rule | Key | Type | Operations | TTL |
|---|---|---|---|---|
| Velocity | `velocity:{customerId}` | Sorted set (member=transactionId, score=epochMs) | `ZADD`, `ZREMRANGEBYSCORE`, `ZCARD` | `2 × window-seconds` (default 120 s) |
| Geo mismatch | `geo:{customerId}` | Hash with fields `country`, `lastSeenMillis` | `HGET`, `HSET` | 7 days |
| New device | `devices:{customerId}` | Set of deviceIds | `SISMEMBER`, `SADD` | 90 days |

VelocityRule fires when `ZCARD > fraud.rules.velocity.max` (default 5) within `fraud.rules.velocity.window-seconds` (default 60).

GeoMismatchRule fires when stored country differs from current AND elapsed time < 1 hour ("impossible travel" proxy). It always updates the stored country/timestamp after evaluation.

NewDeviceRule fires when the deviceId is not already a set member; SADD always runs after the check.

## Kafka Wiring

Topic name constants live in [`KafkaConfig`](../../src/main/java/com/vishavbangotra/fraud_detection_system/config/KafkaConfig.java):

| Constant | Value | Partitions | Replicas |
|---|---|---|---|
| `TRANSACTIONS_RAW` | `transactions.raw` | 3 | 1 |
| `TRANSACTIONS_SCORED` | `transactions.scored` | 3 | 1 |
| `TRANSACTIONS_FLAGGED` | `transactions.flagged` | 3 | 1 |

**Producer** (Spring Boot auto-config from `application.yml`):
- `key-serializer`: `StringSerializer`
- `value-serializer`: `TransactionSerializer`
- `acks`: `all`

**Streams** (Spring Boot auto-config):
- `application-id`: `fraud-detection-streams`
- `default.key.serde`: `Serdes$StringSerde`
- `default.value.serde`: `TransactionSerde`

**Sink consumers** for `ScoredTransaction` are wired manually because they need a different value deserializer. See [`ScoredTransactionConsumerConfig`](../../src/main/java/com/vishavbangotra/fraud_detection_system/consumers/ScoredTransactionConsumerConfig.java):
- Bean: `scoredTxnListenerContainerFactory` (referenced by `@KafkaListener(containerFactory = ...)`)
- `auto-offset-reset`: `earliest`

## Persistence Wiring

JPA auto-config; `spring.jpa.hibernate.ddl-auto: update` creates tables on first boot. Two entities, both keyed by an auto-generated `Long id` with secondary indexes on `transactionId` and `customerId`. `triggeredRules` is stored as a comma-joined string (single column) to avoid a join table at MVP scale.

## Webhook Alert Flow

`WebhookAlertService.send(ScoredTransaction)`:
- If `alerts.webhook.url` is blank/null → debug log and return (no-op).
- Otherwise, attempt POST up to 3 times with 500 ms backoff. On exhaustion, log error — never throws into the listener (would cause re-delivery and double-write).
- Body is the full `ScoredTransaction` JSON (uses Spring's default Jackson, so `Instant` serializes as ISO-8601).

## Reset / Operator Endpoint

`DELETE /api/transactions/reset` clears all three sinks atomically-ish:
1. `deleteAllInBatch()` on both repositories (single transaction).
2. `RedisConnectionFactory.getConnection().serverCommands().flushDb()`.
3. For each topic, use `AdminClient.deleteRecords(beforeOffset=latest)` to truncate.

This is intended for local dev and test runs only — see `decisions.md` for the rationale.

## Configuration Surface

| Property | Default | Effect |
|---|---|---|
| `spring.kafka.bootstrap-servers` | `localhost:9092` | Kafka cluster |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/fraud` | DB |
| `spring.data.redis.host/port` | `localhost:6379` | Redis |
| `fraud.rules.amount.threshold` | `10000` | AmountThresholdRule cutoff |
| `fraud.rules.velocity.max` | `5` | Max txns per window |
| `fraud.rules.velocity.window-seconds` | `60` | Velocity window |
| `alerts.webhook.url` | `${ALERT_WEBHOOK_URL:}` (blank disables) | Webhook target |
