#!/bin/bash

# Install Novu for Testing
# This script installs Novu with testing configuration on k3s

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if helm is available
if ! command -v helm &> /dev/null; then
    echo -e "${RED}Error: helm is not installed or not in PATH${NC}"
    exit 1
fi

# Check if kubectl is available
if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}Error: kubectl is not installed or not in PATH${NC}"
    exit 1
fi

# Create Novu namespace if it doesn't exist
kubectl create namespace novu 2>/dev/null || true

# Add Novu Helm repository
echo -e "${YELLOW}Adding Novu Helm repository...${NC}"
helm repo add nova-edge-charts oci://ghcr.io/nova-edge/charts || true
helm repo update

# Install Novu with testing configuration
echo -e "${YELLOW}Installing Novu with testing configuration...${NC}"

# Use the testing values file
helm install novu nova-edge-charts/novu --namespace novu \
    --values k8s/novu-testing-values.yaml \
    --set mongodb.enabled=true \
    --set redis.enabled=true \
    --set provider.enabled=true \
    --set workflows.enabled=true \
    --set ingress.enabled=false \
    --set service.type=ClusterIP

# Wait for Novu to be ready
echo -e "${YELLOW}Waiting for Novu to be ready...${NC}"
sleep 30

# Check Novu deployment status
novu_deployment=$(kubectl get deployment novu -n novu -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo "0")
if [ "$novu_deployment" = "1" ]; then
    echo -e "${GREEN}✅ Novu deployment is ready${NC}"
else
    echo -e "${YELLOW}Novu may still be starting up...${NC}"
    kubectl get pods -n novu
fi

# Check Novu services
novu_services=$(kubectl get services -n novu | grep -E "(novu|redis|mongodb)" | wc -l)
if [ "$novu_services" -ge 3 ]; then
    echo -e "${GREEN}✅ Novu services are available${NC}"
else
    echo -e "${YELLOW}⚠ Some Novu services may not be ready${NC}"
fi

# Test Novu connectivity
echo -e "${YELLOW}Testing Novu connectivity...${NC}"
novu_pod=$(kubectl get pod -n novu -l app.kubernetes.io/name=novu -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
if [ -n "$novu_pod" ]; then
    if kubectl exec -n novu $novu_pod -- curl -s http://localhost:3000/health > /dev/null 2>&1; then
        echo -e "${GREEN}✅ Novu health check passed${NC}"
    else
        echo -e "${YELLOW}Novu health check failed, but service may still be starting up${NC}"
    fi
else
    echo -e "${YELLOW}Could not find Novu pod${NC}"
fi

echo -e "${GREEN}✅ Novu installation for testing completed!${NC}"
echo -e "${YELLOW}Next steps:${NC}"
echo -e "1. Update k8s/secrets.yaml with your Novu API Key"
echo -e "   (Get key from http://localhost:3000 after Novu setup)"
echo -e "2. Deploy the application: make deploy-k3s-testing"
echo -e "3. Verify setup: make verify-k3s-testing"