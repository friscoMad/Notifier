#!/bin/bash

# Install Novu for Testing
# Starts the Novu stack (MongoDB, Redis, API, Worker, WS, Dashboard) via Docker Compose.
# Based on: https://github.com/novuhq/novu/blob/next/docker/Readme.md

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Check Docker is running
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}Error: Docker is not running. Please start Docker Desktop first.${NC}"
    exit 1
fi

# Start only the Novu services (leave postgres to the app stack)
echo -e "${YELLOW}Starting Novu stack (MongoDB, Redis, API, Worker, WS, Dashboard)...${NC}"
docker compose -f "$REPO_ROOT/docker-compose.yml" up -d \
    mongodb redis api worker ws dashboard

# Wait for MongoDB
echo -e "${YELLOW}Waiting for MongoDB to be ready...${NC}"
for i in {1..20}; do
    if docker exec novu_mongodb mongosh --quiet \
        --username root --password secret \
        --eval "db.adminCommand('ping').ok" > /dev/null 2>&1; then
        echo -e "${GREEN}✅ MongoDB is ready${NC}"
        break
    fi
    if [ "$i" -eq 20 ]; then
        echo -e "${RED}MongoDB did not become ready in time.${NC}"
        exit 1
    fi
    echo -e "${YELLOW}  Waiting for MongoDB ($i/20)...${NC}"
    sleep 5
done

# Wait for Redis
echo -e "${YELLOW}Waiting for Redis to be ready...${NC}"
for i in {1..12}; do
    if docker exec novu_redis redis-cli ping > /dev/null 2>&1; then
        echo -e "${GREEN}✅ Redis is ready${NC}"
        break
    fi
    if [ "$i" -eq 12 ]; then
        echo -e "${RED}Redis did not become ready in time.${NC}"
        exit 1
    fi
    echo -e "${YELLOW}  Waiting for Redis ($i/12)...${NC}"
    sleep 5
done

# Wait for Novu API health check
echo -e "${YELLOW}Waiting for Novu API to be ready (may take a minute on first run)...${NC}"
for i in {1..24}; do
    if curl -sf http://localhost:3000/v1/health-check > /dev/null 2>&1; then
        echo -e "${GREEN}✅ Novu API is ready${NC}"
        break
    fi
    if [ "$i" -eq 24 ]; then
        echo -e "${YELLOW}⚠ Novu API health check did not respond — may still be initialising${NC}"
        echo -e "${YELLOW}Check logs with: docker logs novu_api${NC}"
    else
        echo -e "${YELLOW}  Waiting for Novu API ($i/24)...${NC}"
        sleep 5
    fi
done

echo ""
echo -e "${GREEN}✅ Novu installation complete!${NC}"
echo -e "${YELLOW}Services:${NC}"
docker compose -f "$REPO_ROOT/docker-compose.yml" ps mongodb redis api worker ws dashboard
echo ""
echo -e "  Dashboard : ${GREEN}http://localhost:4000${NC}  (admin@notifier.local / admin123)"
echo -e "  API       : ${GREEN}http://localhost:3000${NC}"
echo -e "  WS        : ${GREEN}http://localhost:3002${NC}"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo -e "  Run the Novu seeder : bash scripts/setup-novu.sh"
