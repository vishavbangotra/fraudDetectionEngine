# Debugging Guide

A symptom → likely cause → fix lookup. Augments [dev-setup.md](dev-setup.md) which covers first-run setup issues.

## Inspection tools, in order of speed

1. **Kafka UI** — http://localhost:8085. See topic offsets, browse messages, view consumer groups (`fraud-detection-streams`, `audit-writer`, `flagged-writer`) and their lag.
2. **App logs** — `./mvnw spring-boot:run` foreground. Look for `Fraud HIGH` warns from the topology, `RuleEngine` initialization line, and `Webhook alert sent` / `Webhook attempt N/3 failed`.
3. **Postgres** — `psql -h localhost -U fraud -d fraud`. Tables: `transaction_events`, `flagged_transactions`.
4. **Redis** — `redis-cli`. Keys: `velocity:*`, `geo:*`, `devices:*`. `KEYS *` is fine in dev but never in prod.
5. **ML sidecar** — `GET http://localhost:8090/health` when `fraud.rules.ml.enabled=true`.
6. **Actuator** — `GET /actuator/health` for the composite, including Kafka, Redis, and DB indicators.

## Decision tree by symptom

### "I posted a transaction but nothing shows up in `transaction_events`"

Walk the pipeline:

```
POST /api/transactions
   │
   ▼
transactions.raw     ← Kafka UI: did the record arrive?
   │
   ▼
FraudDetectionStream ← App logs: any "RuleEngine" errors?
   │
   ▼
transactions.scored  ← Kafka UI: did a scored record show up?
   │
   ▼
AuditConsumer        ← App logs: deserialization or DB errors?
   │
   ▼
transaction_events   ← psql: is the row there?
```

Common stops:
- **Stuck at `transactions.raw`**: producer error in app log; check `acks` (`all`) and broker reachability.
- **Stuck at the topology**: streams app failed to start. Look for `StreamsException` at boot.
- **Stuck at `transactions.scored` → `transaction_events`**: deserialization mismatch or DB unavailable. Check `audit-writer` consumer group lag in Kafka UI.

### "Velocity rule should have fired but didn't"

Inspect Redis:

```bash
redis-cli ZRANGE velocity:cust-1 0 -1 WITHSCORES
redis-cli TTL velocity:cust-1
```

Confirm:
- The expected number of entries is present.
- Their scores (epoch-ms) sit within the configured window (`fraud.rules.velocity.window-seconds`, default 60 000 ms).
- The threshold (`fraud.rules.velocity.max`, default 5) is exceeded — the rule fires on **strictly greater than**, so the 6th transaction is the first to fire.

The rule uses `txn.getTimestamp()` for windowing. If you're posting old timestamps, entries land outside the live window and `ZREMRANGEBYSCORE` removes them.

### "Geo mismatch rule should have fired but didn't"

```bash
redis-cli HGETALL geo:cust-1
```

The rule fires only when (a) `country` differs AND (b) `(now - lastSeenMillis) < 3_600_000`. If you reset Redis between probes, the first transaction has nothing to compare against — it always passes.

### "New device rule fires for the *same* device id"

Check the set:

```bash
redis-cli SMEMBERS devices:cust-1
```

If the device id isn't there, the previous run never actually wrote to the set — usually because the rule threw earlier. Look in app logs for `Rule NEW_DEVICE failed for txn ...`.

### "Webhook isn't firing"

Confirm:

```bash
echo $ALERT_WEBHOOK_URL          # in the shell that started the app
```

The env var is captured at construction time. If you `export` it after `mvnw spring-boot:run`, it's already too late — restart the app.

If the URL is set but no alert arrives:
- Is the event actually HIGH? Check `transaction_events.risk_level`.
- App logs show `Webhook attempt N/3 failed`? The endpoint is unreachable or returning 4xx/5xx. The service swallows after 3 attempts (ADR-006); the alert is lost — check the receiver.
- App logs show `Webhook URL not configured`? The blank-URL no-op fired.

### "`ML_SCORING` never appears in `triggeredRules`"

Confirm the rule is enabled and the sidecar is reachable:

```bash
curl http://localhost:8090/health
```

The Spring app must be started with `--fraud.rules.ml.enabled=true`; otherwise `MlScoringRule` is not registered. If logs show `ML sidecar scoring failed`, the rule fails open and scoring continues with the non-ML rules. If the sidecar responds but the rule still does not fire, lower `fraud.rules.ml.threshold` temporarily or post a high-amount, off-hours transaction.

### "Streams app keeps restarting"

Most common cause: schema mismatch on a topic. The `fraud-detection-streams` consumer group started reading messages it can't deserialize.

Quick fix in dev:

```bash
curl -X DELETE http://localhost:8080/api/transactions/reset
```

This truncates the topics (so pre-schema-change records are gone) and clears Postgres/Redis. Restart the app.

### "JSON deserialization fails for `Instant`"

You introduced a serializer that doesn't go through [`JsonMapper.create()`](../../src/main/java/com/vishavbangotra/fraud_detection_system/serdes/JsonMapper.java). Every Kafka serde in this codebase must use it; vanilla `new ObjectMapper()` cannot serialize `Instant`. See ADR-009.

### "Postgres connection refused"

```bash
docker compose ps postgres
```

Not running → `docker compose up -d postgres`. If the container is up but JDBC still fails, the password may have drifted from the docker-compose env vars (`fraud`/`fraud`/`fraud`).

### "Two rows in `flagged_transactions` for one event"

Only happens if the listener throws after the DB write. Look for an exception in `FlaggedConsumer` between `repository.save(...)` and the webhook call. The listener will redeliver, leading to another row. The webhook itself swallows by design.

## Useful one-liners

```bash
# All HIGH events in the last hour
psql -h localhost -U fraud -d fraud -c \
  "SELECT transaction_id, customer_id, score, triggered_rules
     FROM transaction_events
    WHERE risk_level = 'HIGH'
      AND created_at > now() - interval '1 hour'
    ORDER BY created_at DESC;"

# Per-rule fire counts
psql -h localhost -U fraud -d fraud -c \
  "SELECT triggered_rules, count(*) FROM transaction_events
    GROUP BY triggered_rules ORDER BY 2 DESC;"

# All velocity counters above threshold right now
redis-cli --scan --pattern 'velocity:*' | while read k; do
  n=$(redis-cli ZCARD "$k")
  [ "$n" -gt 5 ] && echo "$k -> $n"
done

# Reset the consumer group offsets (advanced — usually use /reset instead)
docker exec -it kafka kafka-consumer-groups \
  --bootstrap-server kafka:29092 --group audit-writer \
  --reset-offsets --to-earliest --all-topics --execute
```

## When all else fails

Full nuclear reset:

```bash
docker compose down -v       # delete container volumes (Kafka, PG, Redis state)
docker compose up -d
./mvnw spring-boot:run
```

This rebuilds everything from scratch and is the cleanest way to recover from accumulated test garbage.
