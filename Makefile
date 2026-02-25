.PHONY: setup db-migrate run-api run-bot setup-k3s-testing verify-k3s-testing deploy-k3s-testing run-tests manage-k3s

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

# K3s Testing Targets
# These targets help with testing the application on k3s

setup-k3s-testing:
	@echo "Setting up k3s testing environment..."
	@scripts/install-k3s.sh
	@scripts/setup-k3s-testing.sh

verify-k3s-testing:
	@echo "Verifying k3s testing environment..."
	@scripts/verify-k3s-testing.sh

deploy-k3s-testing: build-docker
	@echo "Deploying to k3s testing..."
	@kubectl apply -f k8s/namespace.yaml
	@kubectl apply -f k8s/postgres.yaml
	@kubectl apply -f k8s/secrets-testing.yaml
	@scripts/install-novu-testing.sh
	@kubectl apply -f k8s/api-deployment.yaml
	@kubectl apply -f k8s/bot-deployment.yaml
	@kubectl apply -f k8s/service.yaml
	@make verify-k3s-testing

run-tests:
	@echo "Running tests in k3s environment..."
	@scripts/run-tests.sh

manage-k3s:
	@echo "K3s Management Utilities"
	@echo ""
	@echo "Usage: scripts/manage-k3s.sh [command]"
	@echo ""
	@echo "Commands:"
	@echo "  info          Show cluster information"
	@echo "  resources     Show resource usage"
	@echo "  logs          Show application logs"
	@echo "  endpoints     Show service endpoints"
	@echo "  connectivity  Test connectivity"
	@echo "  restart       Restart all services"
	@echo "  cleanup       Clean up testing environment"
	@echo "  all           Show all resources and logs"
	@echo ""
	@echo "Example: scripts/manage-k3s.sh info"

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
