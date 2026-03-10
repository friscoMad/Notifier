.PHONY: novu-up novu-seed db-migrate run-api run-bot

novu-up:
	@echo "Starting Novu stack..."
	@bash scripts/install-novu-testing.sh

novu-seed:
	@echo "Seeding Novu..."
	@bash scripts/setup-novu.sh

db-migrate:
	@echo "Running database migrations..."
	@cd api && ./gradlew flywayMigrate

run-api:
	@echo "Starting API Service..."
	@cd api && ./gradlew bootRun

run-bot:
	@echo "Starting Slack Bot..."
	@cd bot && ./gradlew bootRun
