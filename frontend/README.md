# Fraud Detection — Frontend

SvelteKit dashboard that consumes the backend's SSE feeds (`/api/stream/transactions`, `/api/stream/alerts`) and drives `POST /api/transactions/simulate` / `DELETE /api/transactions/reset`.

## Layout

Single page (`src/routes/+page.svelte`) with three stacked sections plus a control bar:

```
ControlBar          brand · per-stream connection chips · simulate / reset
KpiCards            throughput · volume(60s) · risk rate · blocked
                    each card: value · sparkline (60s/2s) · trend Δ
ArchitectureDiagram Ingest → Stream → Score → Route → Persist & Alert
                    live counters · animated risk-tinted flow particles
TransactionFeed | AlertsPanel
                    all scored events (filterable) | HIGH-only with rule chips
```

Components live under `src/lib/components/`. Stream wiring (EventSource → Svelte stores, with feed caps of 200 / 50) is in `src/lib/streams.ts`; KPI math is in `src/lib/metrics.ts` and unit-tested by `metrics.test.ts`. See [`docs/architecture/decisions.md` ADR-014](../docs/architecture/decisions.md#adr-014-dashboard-composition--inline-pipeline-diagram-and-sparkline-kpis) for layout rationale.

## Run

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173 (proxies /api -> :8080)
```

## Configure

Copy `.env.example` to `.env` to override the backend base URL:

```
VITE_API_BASE=http://localhost:8080
```

In dev, requests to `/api/...` are proxied via Vite (see `vite.config.ts`). In a static deployment, set `VITE_API_BASE` to the absolute backend URL at build time.

## Build

```bash
npm run build        # outputs to ./build
```

The build is fully static (`adapter-static`), so it can be served by Spring's `src/main/resources/static/`, S3, or any CDN.

## Test

```bash
npm test             # vitest one-shot
npm run test:watch
```
