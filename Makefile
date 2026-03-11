.PHONY: up novu-up novu-seed db-migrate run-api run-bot run-simulator buildkite-agent ngrok-buildkite

up:
	@echo "Starting all infrastructure (Postgres + Novu stack)..."
	@docker compose up -d postgres
	@bash scripts/install-novu-testing.sh

novu-up:
	@echo "Starting Novu stack..."
	@bash scripts/install-novu-testing.sh

novu-seed:
	@echo "Seeding Novu..."
	@bash scripts/setup-novu.sh

db-migrate:
	@echo "Running database migrations..."
	@./gradlew :api:flywayMigrate

run-api:
	@echo "Starting API Service..."
	@./gradlew :api:bootRun

run-bot:
	@echo "Starting Slack Bot..."
	@./gradlew :bot:bootRun

run-simulator:
	@echo "Starting Webhook Simulator..."
	@./gradlew :tools:webhook-simulator:bootRun

buildkite-agent:
	@echo "Starting Buildkite agent (queue: default-self-hosted)..."
	@bash scripts/start-buildkite-agent.sh

ngrok-buildkite:
	@echo "Starting ngrok tunnel for Buildkite webhooks..."
	@bash scripts/start-ngrok-buildkite.sh
