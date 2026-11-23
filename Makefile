# FoodLens India — top-level commands (SPEC §4, §16).
# Targets for services/api and apps/web are no-ops until those trees exist, so `make lint test` stays usable.
ANDROID_DIR := apps/android-consumer
GRADLE := cd $(ANDROID_DIR) && ./gradlew --console=plain

.PHONY: up down seed test lint openapi android-test android-debug android-unit android-lint

up:
	@test -f infra/compose/docker-compose.yml && docker compose -f infra/compose/docker-compose.yml up -d || echo "infra/compose not created yet"

down:
	@test -f infra/compose/docker-compose.yml && docker compose -f infra/compose/docker-compose.yml down || true

seed:
	@echo "seed: services/api not created yet"

test: android-unit
	@test -d services/api && (cd services/api && uv run pytest) || echo "services/api: not created yet"
	@test -d apps/web && (cd apps/web && npm test) || echo "apps/web: not created yet"

lint: android-lint
	@test -d services/api && (cd services/api && uv run ruff check . && uv run mypy --strict app) || echo "services/api: not created yet"
	@test -d apps/web && (cd apps/web && npm run lint && npx tsc --noEmit) || echo "apps/web: not created yet"

openapi:
	@echo "openapi: services/api not created yet"

android-unit:
	$(GRADLE) test

android-lint:
	$(GRADLE) detekt ktlintCheck

android-test:
	$(GRADLE) test lint detekt ktlintCheck

android-debug:
	$(GRADLE) :app:assembleDebug
