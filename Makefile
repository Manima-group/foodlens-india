# AAHAR (FoodLens India) top-level commands (SPEC §4, §16).
# apps/web targets are no-ops until that tree exists, so `make lint test` stays usable.
ANDROID_DIR := apps/android-consumer
GRADLE := cd $(ANDROID_DIR) && ./gradlew --console=plain
COMPOSE := docker compose -f infra/compose/docker-compose.yml
UV := cd services/api && uv run

.PHONY: up down seed api-dev api-seed api-test api-lint test lint openapi \
	android-test android-debug android-unit android-lint web-test web-lint

# --- stack --------------------------------------------------------------------
up:
	$(COMPOSE) up -d --build

down:
	$(COMPOSE) down

seed:
	$(COMPOSE) exec api python -m app.seed

# Without Docker: needs a local PostgreSQL 16 and DATABASE_URL (see .env.example).
api-dev:
	cd services/api && uv run alembic upgrade head && uv run uvicorn app.main:app --reload --host 0.0.0.0 --port 8000

api-seed:
	$(UV) python -m app.seed

# --- checks -------------------------------------------------------------------
test: android-unit api-test web-test

lint: android-lint api-lint web-lint

# Needs PostgreSQL; TEST_DATABASE_URL defaults to postgres@127.0.0.1:5432/foodlens_test.
api-test:
	$(UV) pytest -q

api-lint:
	cd services/api && uv run ruff check . && uv run ruff format --check . && uv run mypy && uv run lint-imports

web-test:
	@test -d apps/web && (cd apps/web && npm test) || echo "apps/web: not created yet"

web-lint:
	@test -d apps/web && (cd apps/web && npm run lint && npx tsc --noEmit) || echo "apps/web: not created yet"

openapi:
	$(UV) python -c "import json; from app.main import app; print(json.dumps(app.openapi(), indent=1, sort_keys=True))" > ../../packages/openapi/openapi.json

# --- android ------------------------------------------------------------------
android-unit:
	$(GRADLE) test

android-lint:
	$(GRADLE) detekt ktlintCheck

android-test:
	$(GRADLE) test lint detekt ktlintCheck

android-debug:
	$(GRADLE) :app:assembleDebug
