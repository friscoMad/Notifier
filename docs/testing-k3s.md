# K3s Testing Environment Setup

This document describes how to set up and test the notification router infrastructure using k3s (k3d) for local development and testing.

## Prerequisites

- Docker Desktop installed and running
- kubectl installed
- Helm 3 installed
- Git
- Java 17+ (for building the application)

## Quick Start

### 1. Install k3s and Setup Testing Environment

```bash
# Install k3s and setup testing infrastructure
make setup-k3s-testing

# Verify the setup
make verify-k3s-testing
```

### 2. Deploy the Application

```bash
# Build Docker images
make build-docker

# Deploy to k3s testing environment  
make deploy-k3s-testing
```

### 3. Run Tests

```bash
# Run the test suite
scripts/run-tests.sh
```

## Detailed Setup Instructions

### Step 1: Install k3s

```bash
# This will install k3s using k3d and create a test cluster
scripts/install-k3s.sh

# Or use the Makefile target
make setup-k3s-testing
```

**What this does:**
- Installs k3d (k3s in Docker)
- Creates a k3s cluster named `notifier-testing`
- Sets up port mappings for local access

### Step 2: Setup Testing Infrastructure

```bash
scripts/setup-k3s-testing.sh

# Or use the Makefile target
make setup-k3s-testing
```

**What this does:**
- Creates the `notification-router` namespace
- Deploys PostgreSQL with persistent storage
- Creates the database and runs migrations
- Installs Novu for notification processing
- Sets up all necessary services

### Step 3: Configure Secrets

```bash
# Copy the testing secrets template
cp k8s/secrets-testing.yaml k8s/secrets.yaml

# Edit k8s/secrets.yaml with your actual credentials:
# - Novu API key (get from http://localhost:3000 after Novu installation)
# - Slack bot token and signing secret
# - GitHub webhook secret (optional)

# Apply the secrets
kubectl apply -f k8s/secrets.yaml
```

### Step 4: Deploy the Application

```bash
# Build Docker images
make build-docker

# Deploy to k3s
make deploy-k3s-testing
```

**What this does:**
- Builds the API and Bot Docker images
- Deploys the API service
- Deploys the Slack bot
- Creates services for external access

## Verification

### Verify Environment Setup

```bash
# Verify the complete setup
make verify-k3s-testing

# Or run the verification script directly
scripts/verify-k3s-testing.sh
```

This will check:
- Kubernetes cluster connectivity
- Namespace creation
- PostgreSQL deployment and connectivity
- Database existence
- Novu installation
- API and Bot service status (if deployed)

### Verify Services

```bash
# Check all pods
kubectl get pods --all-namespaces

# Check services
kubectl get services -n notification-router
kubectl get services -n novu

# Check logs
kubectl logs -n notification-router deployment/postgres
kubectl logs -n novu deployment/novu-api
```

## Testing

### Run Application Tests

```bash
# Run the test suite
scripts/run-tests.sh

# Or run individual test types
./gradlew test          # Unit tests
./gradlew integrationTest  # Integration tests
./gradlew e2eTest        # End-to-end tests
```

### Test API Endpoints

```bash
# Get the API service URL
API_URL=$(kubectl get service notification-router-api -n notification-router -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
if [ -z "$API_URL" ]; then
    API_URL=$(kubectl get service notification-router-api -n notification-router -o jsonpath='{.spec.clusterIP}')
fi

echo "API is available at: http://$API_URL"

# Test health endpoint
curl http://$API_URL/actuator/health

# Test API endpoints
curl http://$API_URL/api/v1/health
curl http://$API_URL/api/v1/subscriptions
```

### Test Slack Bot

```bash
# Get the bot service URL
BOT_URL=$(kubectl get service notification-router-bot -n notification-router -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
if [ -z "$BOT_URL" ]; then
    BOT_URL=$(kubectl get service notification-router-bot -n notification-router -o jsonpath='{.spec.clusterIP}')
fi

echo "Bot is available at: http://$BOT_URL"

# Test bot health (if implemented)
curl http://$BOT_URL/actuator/health
```

## Development Workflow

### Starting Development

1. **Install k3s and setup testing environment:**
   ```bash
   make setup-k3s-testing
   ```

2. **Deploy the application:**
   ```bash
   make deploy-k3s-testing
   ```

3. **Run tests:**
   ```bash
   scripts/run-tests.sh
   ```

### During Development

1. **Make code changes**
2. **Build and redeploy:**
   ```bash
   make build-docker deploy-k3s-testing
   ```
3. **Run tests:**
   ```bash
   scripts/run-tests.sh
   ```

### Cleanup

```bash
# Delete the k3s cluster
k3d cluster delete notifier-testing

# Or delete individual resources
kubectl delete -f k8s/namespace.yaml
kubectl delete -f k8s/postgres.yaml
kubectl delete -f k8s/api-deployment.yaml
kubectl delete -f k8s/bot-deployment.yaml
```

## Troubleshooting

### Common Issues

#### 1. Docker Desktop Not Running
```bash
# Check Docker status
docker info

# If not running, start Docker Desktop
```

#### 2. Kubernetes Cluster Not Available
```bash
# Check cluster status
kubectl cluster-info

# If not available, reinstall k3s
make setup-k3s-testing
```

#### 3. PostgreSQL Not Starting
```bash
# Check PostgreSQL pod
kubectl get pods -n notification-router -l app=postgres

# Check logs
kubectl logs -n notification-router -l app=postgres

# Check persistent volume
kubectl get pvc -n notification-router
```

#### 4. Novu Installation Failing
```bash
# Check Novu pods
kubectl get pods -n novu

# Check Helm releases
helm list -n novu

# Check Novu logs
kubectl logs -n novu deployment/novu-api
```

#### 5. Application Deployment Failing
```bash
# Check application pods
kubectl get pods -n notification-router

# Check events
kubectl get events -n notification-router

# Check deployment status
kubectl describe deployment notification-router-api -n notification-router
```

### Debug Commands

```bash
# Check all resources in namespace
kubectl get all -n notification-router

# Describe specific resources
kubectl describe pod -n notification-router <pod-name>
kubectl describe deployment -n notification-router <deployment-name>

# Check logs
kubectl logs -n notification-router <pod-name>

# Port forward for debugging
kubectl port-forward -n notification-router svc/notification-router-api 8080:80
kubectl port-forward -n novu svc/novu-api 3000:3000
```

## Port Mappings

| Service | Port Mapping | URL |
|---------|--------------|-----|
| API Service | 8080:80 | http://localhost:8080 |
| Load Balancer | 8443:443 | https://localhost:8443 |
| Custom Port | 30000:30000 | http://localhost:30000 |

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Spring profile | `k8s-testing` |
| `NOVU_API_KEY` | Novu API key | From secrets |
| `SLACK_BOT_TOKEN` | Slack bot token | From secrets |
| `DATABASE_URL` | Database URL | `jdbc:postgresql://postgres:5432/notification_router` |

## Testing Profiles

The application supports different Spring profiles:

- `local`: For local development with localhost database
- `k8s`: For production-like Kubernetes deployment
- `k8s-testing`: For k3s testing environment (used by default)

## Performance Testing

For load testing, you can use:

```bash
# Install k6 for load testing
# Run load tests
scripts/load-test.sh
```

## Monitoring

```bash
# Check resource usage
kubectl top pods -n notification-router
kubectl top nodes

# Check cluster health
kubectl get nodes -o wide
kubectl describe nodes
```

## Backup and Restore

```bash
# Backup PostgreSQL data
scripts/backup-postgres.sh

# Restore PostgreSQL data
scripts/restore-postgres.sh
```