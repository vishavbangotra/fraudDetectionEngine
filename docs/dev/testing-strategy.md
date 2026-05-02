# Testing Strategy

## Pyramid

| Layer | Where | Tools | Run by |
|---|---|---|---|
| Pure unit | `src/test/java/.../scoring/`, `.../alerting/` | JUnit 5, Mockito, AssertJ | `./mvnw test` |
| Topology | `src/test/java/.../streams/` | `kafka-streams-test-utils` (`TopologyTestDriver`) | `./mvnw test` |
| End-to-end (manual) | curl + psql + Kafka UI | Docker Compose stack | Operator |

There is no integration-test tier in CI today (Testcontainers is on the [roadmap](../product/roadmap.md), see ADR-010).

## Current test inventory

### `RiskLevelTest` — [scoring/RiskLevelTest.java](../../src/test/java/com/vishavbangotra/fraud_detection_system/scoring/RiskLevelTest.java)
3 tests. Verifies `<30` → LOW, `30..69` → MEDIUM, `≥70` → HIGH. Single source of truth for the risk bands.

### `RuleEngineTest` — [scoring/RuleEngineTest.java](../../src/test/java/com/vishavbangotra/fraud_detection_system/scoring/RuleEngineTest.java)
5 tests. Mocks `Rule` instances to verify:
- No rules fire → score 0, LOW, empty `triggeredRules`.
- Single rule fires → weight applied, correct band.
- Multiple rules fire → sum, ordered names, HIGH band.
- Score caps at 100 even when summed weights exceed it.
- A rule that throws is logged and skipped without aborting the others.

### `AmountThresholdRuleTest` — [scoring/rules/AmountThresholdRuleTest.java](../../src/test/java/com/vishavbangotra/fraud_detection_system/scoring/rules/AmountThresholdRuleTest.java)
5 tests. Boundary checks: at threshold (no fire), above (fire), below (no fire), null amount, null txn.

### `FraudDetectionStreamTest` — [streams/FraudDetectionStreamTest.java](../../src/test/java/com/vishavbangotra/fraud_detection_system/streams/FraudDetectionStreamTest.java)
3 tests using `TopologyTestDriver`:
- Low-risk transaction → only `transactions.scored` (no flagged record).
- High-risk transaction → both `transactions.scored` and `transactions.flagged`.
- Mixed batch → all in scored, only HIGH in flagged.

The `RuleEngine` is mocked; the test asserts the **routing** behavior of the topology, not rule semantics.

### `WebhookAlertServiceTest` — [alerting/WebhookAlertServiceTest.java](../../src/test/java/com/vishavbangotra/fraud_detection_system/alerting/WebhookAlertServiceTest.java)
3 tests:
- Blank URL → no-op.
- Null URL → no-op.
- Unreachable URL → exhausts retries and returns normally (does not throw).

The "happy path" POST is exercised by the manual end-to-end run.

**Total: 19 tests, ~6 s wall-clock.**

## Coverage gaps (deliberate, see ADR-010)

| Component | Why no automated test today |
|---|---|
| `VelocityRule`, `GeoMismatchRule`, `NewDeviceRule` | Need real Redis. Mocking deep `StringRedisTemplate` chains is brittle and tests almost nothing about Redis semantics. Covered by manual e2e. |
| `TransactionEventRepository`, `FlaggedTransactionRepository` | Trivial Spring Data JPA — covered by manual e2e and DB inspection. |
| `AuditConsumer`, `FlaggedConsumer` | Pure glue around the repository + alert service. The wiring is exercised end-to-end. |
| Webhook happy path | Avoiding the WireMock dependency at MVP. |
| Spring context loading | The auto-generated `contextLoads()` test was removed because it requires Postgres + Redis to start. |

These will be filled by Testcontainers tests (roadmap, near-term).

## End-to-end manual procedure

This is the contract that fills the integration-test gap. Run it before merging anything that touches a rule, the topology, persistence, or alerting.

```bash
# 1. Start infra and the app
docker compose up -d
./mvnw spring-boot:run

# 2. Configure a webhook sink (optional)
export ALERT_WEBHOOK_URL='https://webhook.site/<your-id>'

# 3. Reset to a clean slate
curl -X DELETE http://localhost:8080/api/transactions/reset

# 4. Generate load
curl -X POST 'http://localhost:8080/api/transactions/simulate?count=50'

# 5. Verify topics in Kafka UI (http://localhost:8085)
#    - transactions.raw      : 50 records
#    - transactions.scored   : 50 records
#    - transactions.flagged  : the HIGH-risk subset

# 6. Verify Postgres
psql -h localhost -U fraud -d fraud -c \
  "SELECT risk_level, count(*) FROM transaction_events GROUP BY risk_level;"
psql -h localhost -U fraud -d fraud -c \
  "SELECT count(*) FROM flagged_transactions;"
# count(transaction_events) == 50; count(flagged_transactions) == count of HIGH

# 7. Verify webhook receiver received one POST per HIGH event
```

### Per-rule probes

To trigger one rule at a time after a `reset`:

| Rule | How |
|---|---|
| `AMOUNT_THRESHOLD` | One `POST /api/transactions` with `amount > 10000` |
| `VELOCITY` | Six `POST /api/transactions` for the same `customerId` in under 60 s |
| `GEO_MISMATCH` | Two `POST /api/transactions` for the same `customerId`, different `country`, less than 1 hour apart |
| `NEW_DEVICE` | One `POST /api/transactions` for a `customerId` that's already in `devices:{customerId}` Redis set, with a fresh `deviceId` |

After each, check `triggeredRules` on the corresponding `transactions.scored` record (Kafka UI or Postgres).

## When tests fail

1. **A unit test fails** — the change broke a contract. Read the failure; do not loosen the assertion.
2. **A topology test fails** — routing is wrong. Inspect `scored` vs `flagged` output topic counts.
3. **The manual e2e diverges** — most often Kafka or Redis didn't start cleanly. `docker compose down -v && docker compose up -d` and retry.

See [debugging-guide.md](debugging-guide.md) for symptoms and fixes.
