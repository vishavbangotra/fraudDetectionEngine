# Roadmap

Items are grouped by horizon. Each entry: what, why, and rough size. Dates are deliberately omitted — sequence matters more.

## Now (shipped — MVP)

- ✅ HTTP ingest with validation (`POST /api/transactions`)
- ✅ Synthetic load generator (`POST /api/transactions/simulate`)
- ✅ Rules engine + 4 built-in rules (Amount, Velocity, Geo, NewDevice)
- ✅ Optional ML scoring rule backed by a Python Isolation Forest sidecar
- ✅ Kafka Streams scoring topology emitting `transactions.scored` + `transactions.flagged`
- ✅ Postgres persistence (audit + flagged tables)
- ✅ Webhook alerting with retry + blank-URL no-op
- ✅ Dev reset endpoint (`DELETE /api/transactions/reset`)
- ✅ Java unit/topology tests + Python sidecar tests

## Next (clear, sized, near-term)

| Item | Why | Size |
|---|---|---|
| **Flyway migrations, replace `ddl-auto: update`** | Hibernate auto-DDL is unsafe for any deploy beyond a laptop. See ADR-008. | S |
| **Authentication on the controller (especially `DELETE /reset`)** | Today every endpoint is open. Spring Security with a single API-key filter is enough. | S |
| **Testcontainers for Redis + Postgres rule/repo tests** | Removes the only real coverage gap. Skipped at MVP per ADR-010. | M |
| **Prometheus metrics export** | Spring Boot Actuator + Micrometer dependency; expose rule fire counts, scored/flagged rates, webhook success ratio. | M |
| **Grafana dashboard JSON checked into repo** | Pair with the metrics work. Single file under `infra/grafana/`. | S |
| **Webhook DLQ** | A genuinely down webhook silently drops alerts today (ADR-006). Land them in a DLQ topic for replay. | M |

## Later (clear direction, less defined)

| Item | Why | Size |
|---|---|---|
| **Email + Slack alert sinks** | Pluggable `AlertSink` interface; webhook becomes one of many. | M |
| **Per-merchant rules / categories** | Today's Transaction has `merchantId` but no merchant-aware rules. | M |
| **Time-series rollup for dashboarding** | Current SQL queries are fine for triage; aggregate metrics need a TSDB or materialized view. | M |
| **Multi-tenant customer namespacing** | Currently one global customer keyspace. Prefix Redis/SQL keys with a tenant id. | L |

## TBD (no decision made)

- Real production deployment target (Kubernetes? ECS? Single VM?). [TBD]
- Schema registry adoption (Avro/Protobuf vs current JSON). [TBD]
- Long-term retention policy for `transaction_events` (partitioning? archive to S3?). [TBD]
- SLO targets — current end-to-end latency is sub-second on a laptop, but no formal targets exist. [TBD]
- Stream-level exactly-once configuration. [TBD]

## Won't do (intentional non-goals for now)

- A bundled web UI for triage. The webhook payload is rich enough; users wire it into existing tools.
- Production ML training. The sidecar ships a deterministic demo scorer; real model training stays out of band.
- A custom DSL for rules. Java rules are explicit, testable, and don't need a parser.
