#!/bin/bash

# k3s Testing Setup Script
# This script sets up the testing infrastructure on k3s

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if kubectl is available
if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}Error: kubectl is not available. Please install kubectl first.${NC}"
    exit 1
fi

# Check if we're connected to a cluster
if ! kubectl cluster-info > /dev/null 2>&1; then
    echo -e "${RED}Error: Not connected to a Kubernetes cluster.${NC}"
    echo -e "${YELLOW}Please run: scripts/install-k3s.sh${NC}"
    exit 1
fi

echo -e "${GREEN}Setting up k3s testing environment...${NC}"

# Create namespace
kubectl apply -f k8s/namespace.yaml

echo -e "${YELLOW}Deploying PostgreSQL...${NC}"
# Deploy PostgreSQL with persistent storage
kubectl apply -f k8s/postgres.yaml

# Wait for PostgreSQL to be ready
echo -e "${YELLOW}Waiting for PostgreSQL to be ready...${NC}"
sleep 30

# Check PostgreSQL status
postgres_status=$(kubectl get pods -n notification-router -l app=postgres -o jsonpath='{.items[0].status.containerStatuses[0].ready}')
if [ "$postgres_status" != "true" ]; then
    echo -e "${RED}PostgreSQL failed to start.${NC}"
    kubectl logs -n notification-router -l app=postgres
    exit 1
fi

echo -e "${GREEN}PostgreSQL is running.${NC}"

# Create database and run migrations
# We'll use a job to initialize the database
echo -e "${YELLOW}Creating database and running migrations...${NC}"

# Create database initialization job
cat <<EOF | kubectl apply -f -
apiVersion: batch/v1
kind: Job
metadata:
  name: db-init
  namespace: notification-router
spec:
  template:
    spec:
      containers:
      - name: db-init
        image: postgres:15
        command: ["/bin/bash", "-c", "\
          PGPASSWORD=postgres psql -h postgres -U postgres -c 'CREATE DATABASE IF NOT EXISTS notification_router;' && \
          echo 'Database created successfully'\
        "]
        env:
        - name: PGPASSWORD
          value: "postgres"
      restartPolicy: OnFailure
EOF

# Wait for job to complete
sleep 10
job_status=$(kubectl get job db-init -n notification-router -o jsonpath='{.status.succeeded}')
if [ "$job_status" != "1" ]; then
    echo -e "${RED}Database initialization failed.${NC}"
    kubectl logs job/db-init -n notification-router
    exit 1
fi

echo -e "${GREEN}Database created successfully.${NC}"

# Run flyway migrations using a job
echo -e "${YELLOW}Running database migrations...${NC}"

# Create flyway migration job
cat <<EOF | kubectl apply -f -
apiVersion: batch/v1
kind: Job
metadata:
  name: flyway-migrate
  namespace: notification-router
spec:
  template:
    spec:
      containers:
      - name: flyway-migrate
        image: flyway/flyway:latest
        command: ["/bin/bash", "-c", "\
          flyway -url=jdbc:postgresql://postgres:5432/notification_router \
                 -user=postgres \
                 -password=postgres \
                 -locations=classpath:db/migration \
                 migrate\
        "]
      restartPolicy: OnFailure
EOF

# Wait for migrations to complete
sleep 15
migration_status=$(kubectl get job flyway-migrate -n notification-router -o jsonpath='{.status.succeeded}')
if [ "$migration_status" != "1" ]; then
    echo -e "${RED}Database migrations failed.${NC}"
    kubectl logs job/flyway-migrate -n notification-router
    exit 1
fi

echo -e "${GREEN}Database migrations completed successfully.${NC}"

# Install Novu for testing
echo -e "${YELLOW}Installing Novu for testing...${NC}"

# Create Novu namespace
kubectl create namespace novu || true

# Install Novu with testing configuration
helm repo add nova-edge-charts oci://ghcr.io/nova-edge/charts || true
helm repo update

# Install Novu with testing configuration (minimal setup)
helm install novu nova-edge-charts/novu --namespace novu \
    --set mongodb.enabled=false \
    --set redis.enabled=true \
    --set provider.enabled=true \
    --set workflows.enabled=true \
    --set ingress.enabled=false \
    --set service.type=ClusterIP

# Wait for Novu to be ready
echo -e "${YELLOW}Waiting for Novu to be ready...${NC}"
sleep 60

# Check Novu pods
novu_ready=$(kubectl get pods -n novu --field-selector=status.phase=Running | grep -c novu)
if [ "$novu_ready" -lt 2 ]; then
    echo -e "${YELLOW}Novu may still be starting up...${NC}"
    kubectl get pods -n novu
else
    echo -e "${GREEN}Novu is running.${NC}"
fi

echo -e "${GREEN}✅ k3s testing environment setup completed successfully!${NC}"
echo -e "${YELLOW}Next steps:${NC}"
echo -e "${YELLOW}1. Deploy the application: make deploy-k3s-testing${NC}"
echo -e "${YELLOW}2. Verify setup: make verify-k3s-testing${NC}"
echo -e "${YELLOW}3. Run tests: scripts/run-tests.sh${NC}"