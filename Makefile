SHELL := /bin/bash
JAVA_HOME ?= /usr/local/android-studio/jbr
export JAVA_HOME

# ─── Project ───────────────────────────────────────────────────────────────────
APP_NAME   := url-shortener
VERSION    ?= v0-naive
TIMESTAMP  := $(shell date +%Y%m%dT%H%M%S)

# ─── Helpers ───────────────────────────────────────────────────────────────────
.PHONY: help
help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
	  awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'

# ─── Build ─────────────────────────────────────────────────────────────────────
.PHONY: build
build: ## Compile and package (skip tests)
	./mvnw package -DskipTests -q

.PHONY: build-image
build-image: ## Build Docker image
	docker build -t $(APP_NAME):dev .

# ─── Test ──────────────────────────────────────────────────────────────────────
.PHONY: test
test: ## Run all tests (requires Docker for Testcontainers)
	JAVA_HOME=$(JAVA_HOME) ./mvnw test

.PHONY: test-unit
test-unit: ## Run unit tests only (excludes integration tests)
	JAVA_HOME=$(JAVA_HOME) ./mvnw test -Dgroups="unit"

# ─── Stack management ──────────────────────────────────────────────────────────
.PHONY: up
up: ## Start the full stack (app + postgres + redis + prometheus + grafana + loki)
	docker compose -f deploy/docker-compose.yml up -d --build

.PHONY: up-infra
up-infra: ## Start only infrastructure (postgres, redis, prometheus, grafana, loki)
	docker compose -f deploy/docker-compose.yml up -d postgres redis prometheus grafana loki

.PHONY: down
down: ## Stop the full stack
	docker compose -f deploy/docker-compose.yml down

.PHONY: down-volumes
down-volumes: ## Stop the full stack and remove volumes (DATA LOSS)
	@echo "⚠️  This will delete all Postgres and Grafana data. Press Ctrl-C to cancel."
	@sleep 3
	docker compose -f deploy/docker-compose.yml down -v

.PHONY: logs
logs: ## Tail app logs
	docker compose -f deploy/docker-compose.yml logs -f app

.PHONY: ps
ps: ## Show stack container status
	docker compose -f deploy/docker-compose.yml ps

# ─── Database ──────────────────────────────────────────────────────────────────
.PHONY: db-migrate
db-migrate: ## Run Flyway migrations via Maven (requires infra up)
	JAVA_HOME=$(JAVA_HOME) ./mvnw flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/urlshortener \
	  -Dflyway.user=urlshortener -Dflyway.password=urlshortener

.PHONY: db-shell
db-shell: ## Open psql shell in the running Postgres container
	docker compose -f deploy/docker-compose.yml exec postgres \
	  psql -U urlshortener -d urlshortener

# ─── Benchmarking ──────────────────────────────────────────────────────────────
.PHONY: bench
bench: ## Full benchmark run for VERSION (clean stack, seed, warm-up, k6, save, tear down)
	VERSION=$(VERSION) benchmarks/scripts/run-bench.sh

.PHONY: bench-saturation
bench-saturation: ## Ramp load to find the saturation point (RATE = peak req/s of the ramp)
	VERSION=$(VERSION) MODE=saturation RATE=$${RATE:-3000} benchmarks/scripts/run-bench.sh

# ─── Utilities ─────────────────────────────────────────────────────────────────
.PHONY: format
format: ## Format code (placeholder; add google-java-format plugin)
	@echo "TODO: wire up google-java-format"

.PHONY: clean
clean: ## Clean Maven build artifacts
	./mvnw clean -q
