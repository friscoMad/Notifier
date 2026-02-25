.PHONY: setup db-migrate run-api run-bot

setup:
	@echo "Setting up local environment..."
	@kubectl apply -f k8s/namespace.yaml
	@kubectl apply -f k8s/postgres.yaml
	@echo "Waiting for PostgreSQL..."
	@sleep 10
	@echo "Installing Novu via Helm..."
	@helm repo add nova-edge-charts oci://ghcr.io/nova-edge/charts || true
	@helm repo update
	@helm install novu nova-edge-charts/novu --namespace novu --set mongodb.enabled=false --set redis.enabled=true || echo "Novu already installed or failed"

db-migrate:
	@echo "Running database migrations..."
	@cd api && ./gradlew flywayMigrate

run-api:
	@echo "Starting API Service..."
	@cd api && ./gradlew bootRun

run-bot:
	@echo "Starting Slack Bot..."
	@cd bot && ./gradlew bootRun

build-docker:
	@echo "Building Docker images..."
	@docker build -t notification-router/api:dev ./api
	@docker build -t notification-router/bot:dev ./bot

deploy-k8s: build-docker
	@echo "Deploying to k3s..."
	@kubectl apply -f k8s/api-deployment.yaml
	@kubectl apply -f k8s/bot-deployment.yaml
	@kubectl apply -f k8s/service.yaml
