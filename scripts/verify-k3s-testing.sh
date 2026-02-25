#!/bin/bash

# k3s Testing Verification Script
# This script verifies that the k3s testing environment is working correctly

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

echo -e "${GREEN}Verifying k3s testing environment...${NC}"

# Verify namespace exists
namespace_status=$(kubectl get namespace notification-router -o jsonpath='{.status.phase}' 2>/dev/null)
if [ "$namespace_status" != "Active" ]; then
    echo -e "${RED}Namespace 'notification-router' not found or not active.${NC}"
    exit 1
fi

echo -e "${GREEN}Namespace 'notification-router' is active.${NC}"

# Verify PostgreSQL deployment
postgres_deployment=$(kubectl get deployment postgres -n notification-router -o jsonpath='{.status.readyReplicas}' 2>/dev/null)
if [ "$postgres_deployment" != "1" ]; then
    echo -e "${RED}PostgreSQL deployment not ready.${NC}"
    kubectl describe deployment postgres -n notification-router
    exit 1
fi

echo -e "${GREEN}PostgreSQL deployment is ready with 1 replica.${NC}"

# Verify PostgreSQL pod is running
postgres_pod=$(kubectl get pods -n notification-router -l app=postgres -o jsonpath='{.items[0].status.phase}' 2>/dev/null)
if [ "$postgres_pod" != "Running" ]; then
    echo -e "${RED}PostgreSQL pod is not running.${NC}"
    kubectl describe pod -n notification-router -l app=postgres
    exit 1
fi

echo -e "${GREEN}PostgreSQL pod is running.${NC}"

# Test PostgreSQL connectivity
echo -e "${YELLOW}Testing PostgreSQL connectivity...${NC}"
kubectl exec -n notification-router deployment/postgres -- psql -U postgres -c "SELECT version();" > /dev/null 2>&1
if [ $? -ne 0 ]; then
    echo -e "${RED}Failed to connect to PostgreSQL.${NC}"
    exit 1
fi

echo -e "${GREEN}PostgreSQL connectivity test passed.${NC}"

# Verify database exists
echo -e "${YELLOW}Checking if database exists...${NC}"

db_exists=$(kubectl exec -n notification-router deployment/postgres -- psql -U postgres -lqt | cut -d \| -f 1 | grep -qw "notification_router" && echo "exists" || echo "missing")
if [ "$db_exists" != "exists" ]; then
    echo -e "${RED}Database 'notification_router' not found.${NC}"
    exit 1
fi

echo -e "${GREEN}Database 'notification_router' exists.${NC}"

# Verify Novu installation
novu_namespace=$(kubectl get namespace novu -o jsonpath='{.status.phase}' 2>/dev/null)
if [ "$novu_namespace" != "Active" ]; then
    echo -e "${RED}Novu namespace not found or not active.${NC}"
    exit 1
fi

echo -e "${GREEN}Novu namespace is active.${NC}"

# Check Novu pods
novu_pods=$(kubectl get pods -n novu --field-selector=status.phase=Running | grep -c novu)
if [ "$novu_pods" -lt 2 ]; then
    echo -e "${YELLOW}Novu may still be starting up...${NC}"
    kubectl get pods -n novu
else
    echo -e "${GREEN}Novu pods are running.${NC}"
fi

# Check Novu services
novu_services=$(kubectl get services -n novu | grep -E "(novu|redis|mongodb)" | wc -l)
if [ "$novu_services" -lt 3 ]; then
    echo -e "${YELLOW}Some Novu services may not be ready...${NC}"
    kubectl get services -n novu
else
    echo -e "${GREEN}Novu services are available.${NC}"
fi

# Test API connectivity (if deployed)
echo -e "${YELLOW}Checking API connectivity...${NC}"
api_service=$(kubectl get service notification-router-api -n notification-router 2>/dev/null)
if [ -n "$api_service" ]; then
    api_ready=$(kubectl get pods -n notification-router -l app=notification-router-api -o jsonpath='{.items[0].status.phase}' 2>/dev/null)
    if [ "$api_ready" = "Running" ]; then
        echo -e "${GREEN}API service is running.${NC}"
        # Test API health endpoint
        api_health=$(kubectl exec -n notification-router deployment/notification-router-api -- curl -s http://localhost:8080/actuator/health | grep -o '"status":"UP"')
        if [ "$api_health" = '"status":"UP"' ]; then
            echo -e "${GREEN}API health check passed.${NC}"
        else
            echo -e "${YELLOW}API health check failed or endpoint not available.${NC}"
        fi
    else
        echo -e "${YELLOW}API service not deployed or not ready.${NC}"
    fi
else
    echo -e "${YELLOW}API service not deployed.${NC}"
fi

# Test Bot connectivity (if deployed)
echo -e "${YELLOW}Checking Bot connectivity...${NC}"
bot_service=$(kubectl get service notification-router-bot -n notification-router 2>/dev/null)
if [ -n "$bot_service" ]; then
    bot_ready=$(kubectl get pods -n notification-router -l app=notification-router-bot -o jsonpath='{.items[0].status.phase}' 2>/dev/null)
    if [ "$bot_ready" = "Running" ]; then
        echo -e "${GREEN}Bot service is running.${NC}"
    else
        echo -e "${YELLOW}Bot service not deployed or not ready.${NC}"
    fi
else
    echo -e "${YELLOW}Bot service not deployed.${NC}"
fi

# Summary
echo -e ""
echo -e "${GREEN}=== k3s Testing Environment Verification Summary ===${NC}"
echo -e "${GREEN}✅ Namespace: notification-router (Active)${NC}"
echo -e "${GREEN}✅ PostgreSQL: Deployed and Running${NC}"
echo -e "${GREEN}✅ Database: notification_router (Exists)${NC}"
echo -e "${GREEN}✅ Novu: Namespace Active${NC}"
echo -e "${GREEN}✅ Connectivity: PostgreSQL connectivity OK${NC}"

if [ -n "$api_service" ] && [ "$api_ready" = "Running" ]; then
    echo -e "${GREEN}✅ API: Service Deployed and Running${NC}"
else
    echo -e "${YELLOW}⚠ API: Service Not Deployed or Not Ready${NC}"
fi

if [ -n "$bot_service" ] && [ "$bot_ready" = "Running" ]; then
    echo -e "${GREEN}✅ Bot: Service Deployed and Running${NC}"
else
    echo -e "${YELLOW}⚠ Bot: Service Not Deployed or Not Ready${NC}"
fi

# Check for any issues
echo -e ""
if [ "$novu_pods" -lt 2 ]; then
    echo -e "${RED}⚠ Warning: Novu may still be starting up (found $novu_pods pods)${NC}"
fi

if [ "$novu_services" -lt 3 ]; then
    echo -e "${RED}⚠ Warning: Some Novu services may not be ready (found $novu_services services)${NC}"
fi

echo -e ""
echo -e "${GREEN}✅ k3s testing environment verification completed!${NC}"
echo -e "${YELLOW}To deploy the application:${NC}"
echo -e "${YELLOW}  make deploy-k3s-testing${NC}"
echo -e "${YELLOW}To run tests:${NC}"
echo -e "${YELLOW}  scripts/run-tests.sh${NC}"