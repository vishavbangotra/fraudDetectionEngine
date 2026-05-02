# Developer Setup

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 | Project is pinned to Java 21 in [pom.xml](../../pom.xml) |
| Maven | bundled wrapper | Use `./mvnw` — no system Maven needed |
| Docker | recent | Runs Kafka, Redis, Postgres, Kafka UI, ML sidecar |
| Python | 3.11+ optional | Only needed to run sidecar tests outside Docker |
| `curl` | any | For exercising the API |
| `psql` | optional | For inspecting Postgres directly |
| `redis-cli` | optional | For inspecting Redis directly |

## First run

```bash
make dev
```

This starts Kafka, Redis, Postgres, Kafka UI, the ML sidecar, the Spring Boot API, and the Svelte dashboard. The app listens on `http://localhost:8080`; the dashboard listens on `http://localhost:5173`. On first start, Hibernate creates the `transaction_events` and `flagged_transactions` tables (`spring.jpa.hibernate.ddl-auto: update`). Kafka topics are created by the `NewTopic` beans in [`KafkaConfig`](../../src/main/java/com/vishavbangotra/fraud_detection_system/config/KafkaConfig.java).

`make dev` enables the optional ML rule by default. To run only the backend stack:

```bash
make run-backend
```

Use `make stop` to stop Docker services when you are done.

## Common Commands

| Command | What it does |
|---|---|
| `make dev` | Start infra, ML sidecar, backend API, and frontend dashboard |
| `make run-backend` | Start infra, ML sidecar, and backend API only |
| `make run-frontend` | Start only the Svelte dashboard |
| `make test` | Run Java, ML sidecar, and frontend tests |
| `make build` | Build backend jar, frontend static bundle, and ML sidecar image |
| `make smoke` | Check backend and ML sidecar health after startup |
| `make stop` | Stop Docker services |
| `make help` | Show all available targets |

## Smoke test

```bash
# Generate 50 synthetic transactions
curl -X POST 'http://localhost:8080/api/transactions/simulate?count=50'

# In another shell, watch the scored topic
# (Kafka UI is the easier path — http://localhost:8085)

# Inspect persisted rows
psql -h localhost -U fraud -d fraud \
  -c "SELECT risk_level, count(*) FROM transaction_events GROUP BY risk_level;"
psql -h localhost -U fraud -d fraud \
  -c "SELECT count(*) FROM flagged_transactions;"

# Inspect Redis state
redis-cli ZRANGE velocity:cust-1 0 -1 WITHSCORES
redis-cli HGETALL geo:cust-1
redis-cli SMEMBERS devices:cust-1

# Inspect the optional ML sidecar, if enabled
curl http://localhost:8090/health
```

To clear everything between runs:

```bash
curl -X DELETE http://localhost:8080/api/transactions/reset
```

## Configuration

All defaults are in [src/main/resources/application.yml](../../src/main/resources/application.yml). Override per-environment with the standard Spring mechanisms:

| Override | What |
|---|---|
| `ALERT_WEBHOOK_URL=https://...` env | Enable webhook alerts (blank = disabled) |
| `--fraud.rules.amount.threshold=5000` CLI flag | Lower the amount threshold |
| `--fraud.rules.ml.enabled=true` CLI flag | Enable the optional `ML_SCORING` rule |
| `--fraud.rules.ml.threshold=0.8` CLI flag | Require a higher sidecar risk score before `ML_SCORING` fires |
| `application-local.yml` + `--spring.profiles.active=local` | Layer profile-specific overrides |

## Useful local URLs

| URL | What |
|---|---|
| http://localhost:8080/swagger-ui.html | Interactive API explorer |
| http://localhost:8080/v3/api-docs | OpenAPI JSON |
| http://localhost:8080/actuator/health | Health composite |
| http://localhost:8090/health | ML sidecar health |
| http://localhost:8085 | Kafka UI (topics, consumer groups, messages) |

## Receiving webhook alerts locally

The webhook payload is the full `ScoredTransaction` JSON. Easiest sinks:

```bash
# Option A: ad-hoc TCP listener
nc -l 8000

# Option B: webhook.site — copy your URL from the page, then:
export ALERT_WEBHOOK_URL="https://webhook.site/<your-id>"
./mvnw spring-boot:run
```

Set the env var **before** starting the app, since `WebhookAlertService` reads it once at construction.

## Running tests

```bash
make test          # all test suites; installs frontend/Python deps if missing
make test-java     # Java only
make test-python   # ML sidecar only
make test-frontend # frontend only
```

## IDE setup

- Enable Lombok annotation processing.
- Mark `src/main/java` as a sources root, `src/test/java` as a test sources root.
- IntelliJ / VS Code Java will pick up Spring Boot configuration automatically from `application.yml`.

## Common first-run issues

| Symptom | Cause | Fix |
|---|---|---|
| `Connection to localhost:5432 refused` on app start | Postgres container not up | `docker compose up -d postgres` |
| `ML sidecar scoring failed` in logs | Sidecar down or returning an error | `docker compose --profile ml up -d ml-sidecar`; scoring continues without ML |
| Streams stalls, no `transactions.scored` records | Bootstrap servers unreachable | Confirm `docker compose ps` shows kafka healthy on 9092 |
| `Cannot deserialize value of type Instant` | Old code path missing `JavaTimeModule` | Should not happen — all serdes use [`JsonMapper.create()`](../../src/main/java/com/vishavbangotra/fraud_detection_system/serdes/JsonMapper.java). If it does, that file regressed |
| Validation rejects a request you expected to pass | Missing `customerId`/`merchantId`/`country`/`deviceId`/`timestamp` | All are `@NotBlank`/`@NotNull` — see [data-models.md](../architecture/data-models.md) |
