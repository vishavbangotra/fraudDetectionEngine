#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

WITH_FRONTEND="${WITH_FRONTEND:-true}"
ML_ENABLED="${ML_ENABLED:-true}"

SPRING_ARGS="${SPRING_ARGS:-}"
if [ -z "$SPRING_ARGS" ] && [ "$ML_ENABLED" = "true" ]; then
  SPRING_ARGS="--fraud.rules.ml.enabled=true"
fi

cd "$ROOT_DIR"

need_cmd docker
echo "Starting Kafka, Redis, Postgres, Kafka UI, and ML sidecar..."
docker compose --profile ml up -d

pids=""

cleanup() {
  local status=$?
  for pid in $pids; do
    if kill -0 "$pid" >/dev/null 2>&1; then
      kill "$pid" >/dev/null 2>&1 || true
    fi
  done
  exit "$status"
}

trap cleanup INT TERM EXIT

echo "Starting Spring Boot API on http://localhost:8080..."
if [ -n "$SPRING_ARGS" ]; then
  ./mvnw spring-boot:run -Dspring-boot.run.arguments="$SPRING_ARGS" &
else
  ./mvnw spring-boot:run &
fi
pids="$pids $!"

if [ "$WITH_FRONTEND" = "true" ]; then
  ensure_frontend_deps
  echo "Starting Svelte dashboard on http://localhost:5173..."
  (cd "$ROOT_DIR/frontend" && npm run dev -- --host 0.0.0.0) &
  pids="$pids $!"
fi

echo "Dev stack is running. Press Ctrl+C to stop backend/frontend. Use 'make stop' to stop Docker services."

while true; do
  for pid in $pids; do
    if ! kill -0 "$pid" >/dev/null 2>&1; then
      wait "$pid"
      exit $?
    fi
  done
  sleep 2
done
