# Real-Time Fraud Detection System

A production-grade event-driven fraud detection engine that keeps risk scoring **decoupled from the payment path**. Transactions arrive over HTTP, stream through Kafka, get scored asynchronously by a configurable rules engine, and persist immutably to PostgreSQL for audit.

## Why This Architecture?

Most fraud detection blocks the payment pipeline — transactions wait for risk analysis to complete. This system inverts the pattern:

1. **Payment completes immediately** — transactions are accepted over HTTP
2. **Scoring happens in parallel** — Kafka Streams processes events asynchronously
3. **Risk is contextual** — Redis-backed state tracks velocity, geo patterns, device history
4. **ML is optional** — a sidecar Isolation Forest model runs alongside rules, but doesn't gate the stream

If the ML sidecar goes down, payments still get scored with the rule engine. No fallback logic, no coupling, no single point of failure.

---

## Architecture

```
┌──────────────┐
│   HTTP API   │  ← Ingest transactions (POST /api/transactions)
└──────┬───────┘
       │
       ▼
┌──────────────────────────────────────────────────────────┐
│              KAFKA STREAMS TOPOLOGY                      │
│                                                          │
│  transactions.raw → [Rules Engine] → transactions.scored│
│                         ↓ HIGH-RISK                      │
│                    transactions.flagged                  │
└──────────┬─────────────────────────┬────────────────────┘
           │                         │
        ┌──▼──┐            ┌────────▼────────┐
        │REDIS│            │   POSTGRES      │
        │State│            │  Audit Log +    │
        └─────┘            │  Flagged Txns   │
                           └─────────────────┘
                                  │
                          ┌───────▼────────┐
                          │  Webhook Alerts│
                          │  + Dashboard   │
                          └────────────────┘
        
┌──────────────────────────────────────────┐
│   Optional: ML Sidecar (FastAPI)         │
│   - Isolation Forest model               │
│   - Fail-open: ignored if unavailable    │
└──────────────────────────────────────────┘
```

---

## Quick Start

Use the root `Makefile` for normal workflows:

```bash
make dev      # Start: infra + ML sidecar + backend (:8080) + frontend (:5173)
make test     # Run: Java + Python + frontend tests
make build    # Build: jar + SvelteKit bundle + sidecar image
make stop     # Tear down Docker services
```

**Frontend:** Navigate to `http://localhost:5173` for live transaction and alert streams.

Full developer setup: [`docs/dev/dev-setup.md`](docs/dev/dev-setup.md)

---

## Built-In Rules

| Rule | Purpose | Backend |
|------|---------|---------|
| **Amount Threshold** | Flag transactions > $10,000 | Stateless |
| **Velocity** | Flag customer with >5 txns in 60s | Redis (sliding window) |
| **Geo Mismatch** | Flag large geographic jumps | Redis (last location) |
| **New Device** | Flag transactions from unseen device IDs | Redis (device set) |
| **ML Scoring** | Optional Isolation Forest anomaly detection | FastAPI sidecar |

---

## API Endpoints

### Ingest
```bash
POST /api/transactions
Content-Type: application/json
{
  "customerId": "cust-1",
  "transactionId": "txn-abc123",
  "amount": 5000.00,
  "country": "US",
  "deviceId": "dev-iphone-1",
  "timestamp": "2026-05-02T20:11:55Z"
  ...
}
```

### Simulate (for testing)
```bash
POST /api/transactions/simulate?count=50
```

### Reset (clears DB + Kafka + Redis)
```bash
DELETE /api/transactions/reset
```

---

## Production-Ready vs. Demo Features

### ✅ Production-Ready
- ✓ Kafka Streams topology (transactional guarantees)
- ✓ PostgreSQL audit trail (immutable logging)
- ✓ Redis-backed state (velocity, geo, device detection)
- ✓ Webhook alerts with retry logic (3x exponential backoff)
- ✓ Fail-open architecture (ML sidecar optional)
- ✓ Docker Compose multi-service orchestration
- ✓ Structured logging (SLF4J + Logback)

### ⚠️ Demo / Not Production-Ready
- ❌ **No authentication** — all endpoints are open
- ❌ **No authorization** — `/reset` endpoint can wipe all data
- ❌ **No rate limiting** — ingestion endpoint is unbounded
- ❌ **No metrics/observability** — no Prometheus metrics, no request tracing
- ❌ **No migrations** — Hibernate `ddl-auto: update` is not safe for prod
- ❌ **ML sidecar is toy model** — Isolation Forest on raw features, not tuned
- ❌ **No alert DLQ** — failed webhook sends are logged but not persisted

### Before Production:
1. **Add Spring Security** with OAuth2 / API key auth
2. **Implement RBAC** and audit who accessed `/reset`
3. **Switch to Flyway migrations** (deterministic schema management)
4. **Add Micrometer metrics** (transaction throughput, scoring latency, rule triggers)
5. **Implement alert DLQ** (retry high-risk alerts to a dead-letter topic)
6. **Test with Testcontainers** (Redis, PostgreSQL, Kafka)

---

## Tech Stack

| Component | Technology |
|-----------|------------|
| **Backend** | Java 21, Spring Boot 3.5, Spring Kafka |
| **Streaming** | Kafka Streams (stateful, windowed aggregations) |
| **State** | Redis (sliding windows, device sets) |
| **Persistence** | PostgreSQL (audit log, flagged transactions) |
| **ML (Optional)** | Python 3.13, FastAPI, scikit-learn (Isolation Forest) |
| **Frontend** | SvelteKit, real-time WebSocket streams |
| **Orchestration** | Docker Compose |

---

## Development

### Run Locally
```bash
make dev
```

This starts:
- PostgreSQL (:5432)
- Redis (:6379)
- Kafka (:9092)
- Spring Boot backend (:8080)
- ML sidecar (:8001, optional)
- SvelteKit frontend (:5173)

### Test

```bash
make test
```

Runs Spring Boot tests, Python FastAPI tests, and SvelteKit frontend tests.

### Logs

```bash
# Backend
docker logs -f fraud-detection-system

# ML sidecar
docker logs -f fraud-ml-sidecar

# Kafka
docker logs -f kafka
```

---

## Design Decisions

### 1. Why decouple scoring from the payment path?
**Reason:** Payment systems need low latency and high availability. Risk analysis can afford to be slightly delayed. By running async, we unblock the payment and let fraud detection be independently scalable.

### 2. Why Redis for state?
**Reason:** Velocity, geo, and device patterns need sub-millisecond lookups. Postgres would create query storms. Redis is perfect for windowed aggregations and device tracking.

### 3. Why is the ML sidecar optional?
**Reason:** ML models break. Hyperparameters drift. If your fraud model crashes, you don't want it to crash payments. The sidecar runs best-effort; if it's down, the rule engine keeps scoring.

### 4. Why Kafka Streams instead of writing custom stream logic?
**Reason:** Kafka Streams handles exactly-once semantics, state management, and topology resilience so you don't have to. The alternative (custom consumer + manual offset tracking) is fragile.

---

## Next Steps

- [ ] Add Spring Security (OAuth2 or API key auth)
- [ ] Implement Flyway schema migrations
- [ ] Set up Testcontainers for integration tests (Redis, Postgres, Kafka)
- [ ] Add Prometheus metrics (transaction/sec, scoring latency, rule triggers)
- [ ] Implement alert dead-letter queue (persist failed webhook sends)
- [ ] Add distributed tracing (Sleuth + Jaeger)
- [ ] Tune ML model (feature engineering, hyperparameter optimization)

---

## References

- [Kafka Streams Topology Design](https://kafka.apache.org/documentation/streams/architecture)
- [Redis Sorted Sets for Windowed Aggregation](https://redis.io/docs/data-types/sorted-sets/)
- [Spring Boot PostgreSQL Setup](https://docs.spring.io/spring-boot/reference/features/sql.html)
- [SvelteKit Real-Time Streams](https://kit.svelte.dev/)
