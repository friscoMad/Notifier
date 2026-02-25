# Phase 1: Infrastructure Setup

## Prerequisites
- Docker Desktop installed and running
- At least 4GB RAM available for containers

## Quick Start

1. **Run the setup script:**
   ```bash
   ./setup-infrastructure.sh
   ```

2. **Wait for services to start** (takes 2-3 minutes)

3. **Verify the setup:**
   ```bash
   ./verify-infrastructure.sh
   ```

## Manual Setup Instructions

### 1. Create Docker Network
```bash
# Create a dedicated network for the notification router
docker network create notification-router-network
```

### 2. Start PostgreSQL
```bash
# Start PostgreSQL container
docker run -d \
    --name notification-router-postgres \
    --network notification-router-network \
    --env POSTGRES_PASSWORD=postgres \
    --env POSTGRES_DB=notification_router \
    --publish 5432:5432 \
    --volume postgres-data:/var/lib/postgresql/data \
    postgres:15
```

### 3. Start MongoDB (required for Novu)
```bash
# Start MongoDB container
docker run -d \
    --name notification-router-mongo \
    --network notification-router-network \
    --publish 27017:27017 \
    --volume mongo-data:/data/db \
    mongo:6
```

### 4. Start Novu
```bash
# Start Novu container
docker run -d \
    --name notification-router-novu \
    --network notification-router-network \
    --publish 3000:3000 \
    --env NOVUSDK_PORT=3000 \
    --env NOVUSDK_MONGO_URL=mongodb://mongo:27017/novu \
    --env NOVUSDK_JWT_SECRET=your-super-secret-jwt-key \
    --env NOVUSDK_EMAIL_HOST=smtp.gmail.com \
    --env NOVUSDK_EMAIL_PORT=587 \
    --env NOVUSDK_EMAIL_USER=your-email@gmail.com \
    --env NOVUSDK_EMAIL_PASS=your-email-password \
    --env NOVUSDK_SMTP_SECURE=false \
    --env NOVUSDK_SMTP_REQUIRE_TLS=true \
    novu/novu:latest
```

## Verification

### Check if all containers are running:
```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

### Expected Output:
```
NAME                            STATUS              PORTS
notification-router-postgres    Up 5 minutes       0.0.0.0:5432->5432/tcp
notification-router-mongo       Up 5 minutes       0.0.0.0:27017->27017/tcp
notification-router-novu        Up 5 minutes       0.0.0.0:3000->3000/tcp
```

## Obtain Novu API Key

1. Open http://localhost:3000 in your browser
2. Sign up for a new account
3. Go to Settings → API Keys
4. Copy the API Key and add it to `k8s/secrets.yaml`

## Update Secrets

Edit `k8s/secrets.yaml` and replace `YOUR_NOVU_API_KEY` with your actual Novu API Key:
```yaml
stringData:
  novu-api-key: "your-actual-novu-api-key-here"
```

## Next Steps

- **Phase 2**: Core API - User management, subscriptions, preferences
- **Phase 3**: Webhooks - GitHub, GitHub Actions, Buildkite adapters
- **Phase 4**: Slack Bot - Commands, interactive modals
- **Phase 5**: Novu Integration - Workflows, digest, providers

## Troubleshooting

### If containers don't start:
```bash
# Check logs
docker logs notification-router-postgres
docker logs notification-router-mongo
docker logs notification-router-novu

# Restart containers
docker restart notification-router-postgres
docker restart notification-router-mongo
docker restart notification-router-novu

# Remove and recreate if needed
docker rm -f notification-router-postgres
docker rm -f notification-router-mongo
docker rm -f notification-router-novu
```

### Port conflicts:
If ports 5432, 27017, or 3000 are already in use, remove the `--publish` flags or change the host ports.