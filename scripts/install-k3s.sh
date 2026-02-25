#!/bin/bash

# k3s Installation Script for Testing
# This script installs k3s using k3d in Docker Desktop

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}Error: Docker is not running. Please start Docker Desktop first.${NC}"
    exit 1
fi

# Install k3d
echo -e "${GREEN}Installing k3d...${NC}"
if ! command -v k3d &> /dev/null; then
    curl -s https://raw.githubusercontent.com/rancher/k3d/main/install.sh | bash
    echo -e "${GREEN}k3d installed successfully.${NC}"
else
    echo -e "${YELLOW}k3d already installed. Updating to latest version...${NC}"
    curl -s https://raw.githubusercontent.com/rancher/k3d/main/install.sh | bash
fi

# Verify k3d installation
if ! command -v k3d &> /dev/null; then
    echo -e "${RED}Failed to install k3d.${NC}"
    exit 1
fi

echo -e "${GREEN}k3d version: $(k3d version)${NC}"

# Create k3s cluster for testing
echo -e "${GREEN}Creating k3s cluster for testing...${NC}"
k3d cluster create notifier-testing \
    --port "8080:80@loadbalancer" \
    --port "8443:443@loadbalancer" \
    --port "30000:30000@loadbalancer" \
    --agents 2 \
    --servers 1 \
    --timeout 300s \
    --wait

# Verify cluster creation
if kubectl cluster-info > /dev/null 2>&1; then
    echo -e "${GREEN}Kubernetes cluster created successfully!${NC}"
    echo -e "${GREEN}Cluster info:${NC}"
    kubectl cluster-info
    echo -e "${GREEN}Nodes:${NC}"
    kubectl get nodes
else
    echo -e "${RED}Failed to create Kubernetes cluster.${NC}"
    exit 1
fi

echo -e "${GREEN}✅ k3s installation and cluster setup completed successfully!${NC}"
echo -e "${YELLOW}To use the cluster:${NC}"
echo -e "${YELLOW}  kubectl cluster-info${NC}"
echo -e "${YELLOW}  kubectl get pods --all-namespaces${NC}"