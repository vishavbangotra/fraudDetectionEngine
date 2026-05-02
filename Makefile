.DEFAULT_GOAL := help

.PHONY: help setup dev run run-backend run-frontend test test-java test-python test-frontend build stop logs smoke clean

help: ## Show available commands
	@awk 'BEGIN {FS = ":.*##"; printf "\nUsage: make <target>\n\nTargets:\n"} /^[a-zA-Z0-9_-]+:.*##/ {printf "  %-16s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

setup: ## Install frontend and ML sidecar dependencies
	@bash -lc 'source scripts/lib.sh && ensure_frontend_deps && ensure_sidecar_venv'

dev: ## Start infra, ML sidecar, backend API, and frontend dashboard
	@bash scripts/dev.sh

run: dev ## Alias for dev

run-backend: ## Start infra, ML sidecar, and backend API only
	@WITH_FRONTEND=false bash scripts/dev.sh

run-frontend: ## Start only the frontend dashboard
	@bash -lc 'source scripts/lib.sh && ensure_frontend_deps && cd frontend && npm run dev -- --host 0.0.0.0'

test: ## Run Java, ML sidecar, and frontend tests
	@bash scripts/test-all.sh

test-java: ## Run Java tests only
	@./mvnw test

test-python: ## Run ML sidecar tests only
	@bash -lc 'source scripts/lib.sh && run_sidecar_tests'

test-frontend: ## Run frontend tests only
	@bash -lc 'source scripts/lib.sh && run_frontend_tests'

build: ## Build backend jar, frontend bundle, and ML sidecar image
	@bash scripts/build-all.sh

stop: ## Stop Docker services
	@docker compose --profile ml down

logs: ## Follow Docker service logs
	@docker compose --profile ml logs -f

smoke: ## Check backend and sidecar health after make dev
	@curl -fsS http://localhost:8080/actuator/health
	@printf "\n"
	@curl -fsS http://localhost:8090/health
	@printf "\n"

clean: ## Remove generated backend/frontend/sidecar build outputs
	@./mvnw clean
	@rm -rf frontend/build frontend/.svelte-kit ml-sidecar/.venv
