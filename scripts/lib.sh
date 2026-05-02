#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

need_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 1
  fi
}

ensure_frontend_deps() {
  need_cmd npm
  if [ ! -x "$ROOT_DIR/frontend/node_modules/.bin/vite" ] \
    || [ ! -x "$ROOT_DIR/frontend/node_modules/.bin/vitest" ]; then
    echo "Installing frontend dependencies..."
    (cd "$ROOT_DIR/frontend" && npm install)
  fi
}

ensure_sidecar_venv() {
  need_cmd python3
  local venv_dir="$ROOT_DIR/ml-sidecar/.venv"
  if [ ! -x "$venv_dir/bin/python" ]; then
    echo "Creating ML sidecar Python environment..."
    python3 -m venv "$venv_dir"
  fi

  echo "Installing ML sidecar dependencies..."
  "$venv_dir/bin/python" -m pip install -r "$ROOT_DIR/ml-sidecar/requirements.txt"
}

run_sidecar_tests() {
  ensure_sidecar_venv
  "$ROOT_DIR/ml-sidecar/.venv/bin/python" -m pytest "$ROOT_DIR/ml-sidecar"
}

run_frontend_tests() {
  ensure_frontend_deps
  (cd "$ROOT_DIR/frontend" && npm test)
}

run_frontend_build() {
  ensure_frontend_deps
  (cd "$ROOT_DIR/frontend" && npm run build)
}
