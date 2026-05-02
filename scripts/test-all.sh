#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

cd "$ROOT_DIR"

echo "Running Java tests..."
./mvnw test

if [ "${SKIP_PYTHON:-false}" != "true" ]; then
  echo "Running ML sidecar tests..."
  run_sidecar_tests
fi

if [ "${SKIP_FRONTEND:-false}" != "true" ]; then
  echo "Running frontend tests..."
  run_frontend_tests
fi
