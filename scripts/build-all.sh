#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

cd "$ROOT_DIR"

echo "Building Spring Boot application..."
./mvnw -DskipTests package

echo "Building frontend..."
run_frontend_build

need_cmd docker
echo "Building ML sidecar image..."
docker compose --profile ml build ml-sidecar
