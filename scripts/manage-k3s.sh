#!/bin/bash

# K3s Management Script
# This script provides utilities for managing the k3s testing environment

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
    exit 1
fi

# Function to show cluster info
show_cluster_info() {
    echo -e "${GREEN}=== K3s Cluster Information ===${NC}"
    echo -e "${YELLOW}Cluster Info:${NC}"
    kubectl cluster-info
    echo ""
    echo -e "${YELLOW}Nodes:${NC}"
    kubectl get nodes
    echo ""
    echo -e "${YELLOW}Namespaces:${NC}"
    kubectl get namespaces
    echo ""
    echo -e "${YELLOW}Persistent Volumes:${NC}"
    kubectl get pv
    echo ""
    echo -e "${YELLOW}Persistent Volume Claims:${NC}"
    kubectl get pvc --all-namespaces
}

# Function to show all resources
show_all_resources() {
    echo -e "${GREEN}=== All Kubernetes Resources ===${NC}"
    echo -e "${YELLOW}Namespaces:${NC}"
    kubectl get namespaces
    echo ""
    echo -e "${YELLOW}Deployments:${NC}"
    kubectl get deployments --all-namespaces
    echo ""
    echo -e "${YELLOW}Pods:${NC}"
    kubectl get pods --all-namespaces
    echo ""
    echo -e "${YELLOW}Services:${NC}"
    kubectl get services --all-namespaces
    echo ""
    echo -e "${YELLOW}Jobs:${NC}"
    kubectl get jobs --all-namespaces
    echo ""
    echo -e "${YELLOW}Persistent Volumes:${NC}"
    kubectl get pv
    echo ""
    echo -e "${YELLOW}Persistent Volume Claims:${NC}"
    kubectl get pvc --all-namespaces
}

# Function to show logs for all services
show_all_logs() {
    echo -e "${GREEN}=== Application Logs ===${NC}"
    
    # Show PostgreSQL logs
    echo -e "${YELLOW}PostgreSQL Logs:${NC}"
    kubectl logs -n notification-router deployment/postgres --tail=20 || echo "No PostgreSQL logs found"
    echo ""
    
    # Show API logs
    echo -e "${YELLOW}API Logs:${NC}"
    kubectl logs -n notification-router deployment/notification-router-api --tail=20 2>/dev/null || echo "API not deployed or no logs"
    echo ""
    
    # Show Bot logs
    echo -e "${YELLOW}Bot Logs:${NC}"
    kubectl logs -n notification-router deployment/notification-router-bot --tail=20 2>/dev/null || echo "Bot not deployed or no logs"
    echo ""
    
    # Show Novu logs
    echo -e "${YELLOW}Novu Logs:${NC}"
    kubectl logs -n novu -l app.kubernetes.io/name=novu --tail=20 2>/dev/null || echo "Novu not deployed or no logs"
}

# Function to cleanup testing environment
cleanup_environment() {
    echo -e "${YELLOW}Cleaning up k3s testing environment...${NC}"
    
    # Delete application deployments
    echo -e "${YELLOW}Deleting application deployments...${NC}"
    kubectl delete deployment notification-router-api notification-router-bot -n notification-router 2>/dev/null || true
    kubectl delete service notification-router-api notification-router-bot -n notification-router 2>/dev/null || true
    
    # Delete database initialization and migration jobs
    echo -e "${YELLOW}Deleting database jobs...${NC}"
    kubectl delete job db-init flyway-migrate -n notification-router 2>/dev/null || true
    
    # Delete namespace (optional)
    echo -e "${YELLOW}Deleting namespace notification-router...${NC}"
    kubectl delete namespace notification-router 2>/dev/null || true
    
    echo -e "${GREEN}Cleanup completed.${NC}"
}

# Function to restart services
restart_services() {
    echo -e "${YELLOW}Restarting services...${NC}"
    
    # Restart API
    echo -e "${YELLOW}Restarting API...${NC}"
    kubectl rollout restart deployment/notification-router-api -n notification-router 2>/dev/null || echo "API not deployed"
    
    # Restart Bot
    echo -e "${YELLOW}Restarting Bot...${NC}"
    kubectl rollout restart deployment/notification-router-bot -n notification-router 2>/dev/null || echo "Bot not deployed"
    
    # Restart Novu
    echo -e "${YELLOW}Restarting Novu...${NC}"
    kubectl rollout restart deployment/novu -n novu 2>/dev/null || echo "Novu not deployed"
    
    # Restart PostgreSQL
    echo -e "${YELLOW}Restarting PostgreSQL...${NC}"
    kubectl rollout restart deployment/postgres -n notification-router 2>/dev/null || echo "PostgreSQL not deployed"
    
    echo -e "${GREEN}Services restarted.${NC}"
}

# Function to check resource usage
check_resources() {
    echo -e "${GREEN}=== Resource Usage ===${NC}"
    echo -e "${YELLOW}Node Resource Usage:${NC}"
    kubectl top nodes 2>/dev/null || echo "Node metrics not available"
    echo ""
    echo -e "${YELLOW}Pod Resource Usage:${NC}"
    kubectl top pods --all-namespaces 2>/dev/null || echo "Pod metrics not available"
}

# Function to show service endpoints
show_endpoints() {
    echo -e "${GREEN}=== Service Endpoints ===${NC}"
    
    # Show API endpoint
    api_service=$(kubectl get service notification-router-api -n notification-router -o jsonpath='{.spec.clusterIP}:{.spec.ports[0].port}' 2>/dev/null || echo "Not available")
    echo -e "${YELLOW}API Endpoint:${NC} $api_service"
    
    # Show Bot endpoint
    bot_service=$(kubectl get service notification-router-bot -n notification-router -o jsonpath='{.spec.clusterIP}:{.spec.ports[0].port}' 2>/dev/null || echo "Not available")
    echo -e "${YELLOW}Bot Endpoint:${NC} $bot_service"
    
    # Show PostgreSQL endpoint
    postgres_service=$(kubectl get service postgres -n notification-router -o jsonpath='{.spec.clusterIP}:{.spec.ports[0].port}' 2>/dev/null || echo "Not available")
    echo -e "${YELLOW}PostgreSQL Endpoint:${NC} $postgres_service"
    
    # Show Novu endpoint
    novu_service=$(kubectl get service -n novu -l app.kubernetes.io/name=novu -o jsonpath='{.items[0].spec.clusterIP}:{.items[0].spec.ports[0].port}' 2>/dev/null || echo "Not available")
    echo -e "${YELLOW}Novu Endpoint:${NC} $novu_service"
}

# Function to test connectivity
test_connectivity() {
    echo -e "${GREEN}=== Connectivity Tests ===${NC}"
    
    # Test API connectivity
    echo -e "${YELLOW}Testing API connectivity...${NC}"
    kubectl exec -n notification-router deployment/notification-router-api -- curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health 2>/dev/null || echo "API not available"
    echo ""
    
    # Test database connectivity
    echo -e "${YELLOW}Testing database connectivity...${NC}"
    kubectl exec -n notification-router deployment/notification-router-api -- nc -zv postgres 5432 2>/dev/null && echo "Database OK" || echo "Database not reachable"
    echo ""
    
    # Test Novu connectivity
    echo -e "${YELLOW}Testing Novu connectivity...${NC}"
    novu_pod=$(kubectl get pod -n novu -l app.kubernetes.io/name=novu -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
    if [ -n "$novu_pod" ]; then
        kubectl exec -n novu $novu_pod -- curl -s -o /dev/null -w "%{http_code}" http://localhost:3000/health 2>/dev/null && echo "Novu OK" || echo "Novu not reachable"
    else
        echo "Novu pod not found"
    fi
}

# Function to show help
show_help() {
    echo -e "${GREEN}K3s Management Script${NC}"
    echo ""
    echo -e "${YELLOW}Usage:${NC} $0 [command]"
    echo ""
    echo -e "${YELLOW}Commands:${NC}"
    echo "  info          Show cluster information"
    echo "  resources     Show resource usage"
    echo "  logs          Show application logs"
    echo "  endpoints     Show service endpoints"
    echo "  connectivity  Test connectivity"
    echo "  restart       Restart all services"
    echo "  cleanup       Clean up testing environment"
    echo "  all           Show all resources and logs"
    echo "  help          Show this help message"
    echo ""
    echo -e "${YELLOW}Examples:${NC}"
    echo "  $0 info"
    echo "  $0 logs"
    echo "  $0 cleanup"
}

# Main script logic
case "${1:-help}" in
    "info")
        show_cluster_info
        ;;
    "resources")
        check_resources
        ;;
    "logs")
        show_all_logs
        ;;
    "endpoints")
        show_endpoints
        ;;
    "connectivity")
        test_connectivity
        ;;
    "restart")
        restart_services
        ;;
    "cleanup")
        cleanup_environment
        ;;
    "all")
        show_all_resources
        show_all_logs
        ;;
    "help"|*)
        show_help
        ;;
esac