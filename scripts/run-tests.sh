#!/bin/bash

# Run Tests in K3s Environment
# This script runs tests against the deployed k3s testing environment

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if kubectl is available
if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}Error: kubectl is not installed or not in PATH${NC}"
    exit 1
fi

# Check if we're connected to a cluster
if ! kubectl cluster-info > /dev/null 2>&1; then
    echo -e "${RED}Error: Not connected to a Kubernetes cluster.${NC}"
    echo -e "Please run: scripts/install-k3s.sh first"
    exit 1
fi

echo -e "${GREEN}Running tests in k3s environment...${NC}"

# Check if services are deployed
echo -e "${YELLOW}Checking deployed services...${NC}"

# Check API service
api_deployment=$(kubectl get deployment notification-router-api -n notification-router -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo "0")
if [ "$api_deployment" = "1" ]; then
    echo -e "${GREEN}✅ API service is deployed and ready${NC}"
else
    echo -e "${RED}❌ API service is not deployed or not ready${NC}"
    exit 1
fi

# Check Bot service
bot_deployment=$(kubectl get deployment notification-router-bot -n notification-router -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo "0")
if [ "$bot_deployment" = "1" ]; then
    echo -e "${GREEN}✅ Bot service is deployed and ready${NC}"
else
    echo -e "${YELLOW}⚠ Bot service is not deployed or not ready${NC}"
fi

# Test API health endpoint
echo -e "${YELLOW}Testing API health...${NC}"
api_health=$(kubectl exec -n notification-router deployment/notification-router-api -- curl -s http://localhost:8080/actuator/health | grep -o '"status":"UP"' || echo "DOWN")
if [ "$api_health" = '"status":"UP"' ]; then
    echo -e "${GREEN}✅ API health check passed${NC}"
else
    echo -e "${RED}❌ API health check failed${NC}"
    exit 1
fi

# Test database connectivity
echo -e "${YELLOW}Testing database connectivity...${NC}"
if kubectl exec -n notification-router deployment/notification-router-api -- nc -zv postgres 5432 > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Database connectivity test passed${NC}"
else
    echo -e "${RED}❌ Database connectivity test failed${NC}"
    exit 1
fi

# Test Novu connectivity
echo -e "${YELLOW}Testing Novu connectivity...${NC}"
novu_pod=$(kubectl get pod -n novu -l app.kubernetes.io/name=novu -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
if [ -n "$novu_pod" ]; then
    if kubectl exec -n novu $novu_pod -- curl -s http://localhost:3000/health > /dev/null 2>&1; then
        echo -e "${GREEN}✅ Novu connectivity test passed${NC}"
    else
        echo -e "${RED}❌ Novu connectivity test failed${NC}"
        exit 1
    fi
else
    echo -e "${RED}❌ Could not find Novu pod${NC}"
    exit 1
fi

# Run integration tests
echo -e "${YELLOW}Running integration tests...${NC}"

# Test webhook endpoint
webhook_test=$(kubectl exec -n notification-router deployment/notification-router-api -- curl -s -X POST http://localhost:8080/webhook/test -H "Content-Type: application/json" -d '{"test": "integration"}' | grep -o '"status":"OK"' || echo "FAILED")
if [ "$webhook_test" = '"status":"OK"' ]; then
    echo -e "${GREEN}✅ Webhook endpoint test passed${NC}"
else
    echo -e "${YELLOW}⚠ Webhook endpoint test failed or endpoint not available${NC}"
fi

# Test API endpoints
echo -e "${YELLOW}Testing API endpoints...${NC}"

# Test health endpoint
health_test=$(kubectl exec -n notification-router deployment/notification-router-api -- curl -s http://localhost:8080/actuator/health | grep -o '"status":"UP"' || echo "FAILED")
if [ "$health_test" = '"status":"UP"' ]; then
    echo -e "${GREEN}✅ Health endpoint test passed${NC}"
else
    echo -e "${RED}❌ Health endpoint test failed${NC}"
    exit 1
fi

# Test API info endpoint
info_test=$(kubectl exec -n notification-router deployment/notification-router-api -- curl -s http://localhost:8080/actuator/info | grep -o '"app"' || echo "FAILED")
if [ "$info_test" = '"app"' ]; then
    echo -e "${GREEN}✅ Info endpoint test passed${NC}"
else
    echo -e "${YELLOW}⚠ Info endpoint test failed or endpoint not available${NC}"
fi

# Test user endpoint
user_test=$(kubectl exec -n notification-router deployment/notification-router-api -- curl -s http://localhost:8080/api/users | grep -o '"users"' || echo "FAILED")
if [ "$user_test" = '"users"' ]; then
    echo -e "${GREEN}✅ Users endpoint test passed${NC}"
else
    echo -e "${YELLOW}⚠ Users endpoint test failed or endpoint not available${NC}"
fi

# Test subscription endpoint
sub_test=$(kubectl exec -n notification-router deployment/notification-router-api -- curl -s http://localhost:8080/api/subscriptions | grep -o '"subscriptions"' || echo "FAILED")
if [ "$sub_test" = '"subscriptions"' ]; then
    echo -e "${GREEN}✅ Subscriptions endpoint test passed${NC}"
else
    echo -e "${YELLOW}⚠ Subscriptions endpoint test failed or endpoint not available${NC}"
fi

# Test notification types endpoint
ntype_test=$(kubectl exec -n notification-router deployment/notification-router-api -- curl -s http://localhost:8080/api/notification-types | grep -o '"notificationTypes"' || echo "FAILED")
if [ "$ntype_test" = '"notificationTypes"' ]; then
    echo -e "${GREEN}✅ Notification types endpoint test passed${NC}"
else
    echo -e "${YELLOW}⚠ Notification types endpoint test failed or endpoint not available${NC}"
fi

# Performance test - simple load test
echo -e "${YELLOW}Running simple load test...${NC}"

# Test concurrent requests
load_test=$(kubectl exec -n notification-router deployment/notification-router-api -- bash -c '
for i in {1..10}; do
    curl -s http://localhost:8080/actuator/health &
done
wait
' | grep -c '"status":"UP"' || echo "0")

if [ "$load_test" -gt 0 ]; then
    echo -e "${GREEN}✅ Load test passed ($load_test successful requests)${NC}"
else
    echo -e "${RED}❌ Load test failed${NC}"
    exit 1
fi

echo -e "${GREEN}✅ All tests completed successfully!${NC}"
echo -e "${YELLOW}Test Summary:${NC}"
echo -e "${GREEN}✓ API service deployed and ready${NC}"
echo -e "${GREEN}✓ Database connectivity OK${NC}"
echo -e "${GREEN}✓ Novu connectivity OK${NC}"
echo -e "${GREEN}✓ Health endpoint working${NC}"
echo -e "${GREEN}✓ Load test passed${NC}"
echo -e "${YELLOW}✓ Some endpoints may not be available without proper configuration${NC}"
echo -e ""
echo -e "${GREEN}System is ready for functional testing!${NC}"