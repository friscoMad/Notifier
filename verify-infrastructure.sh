#!/bin/bash

# Phase 1: Infrastructure Verification
# This script verifies the infrastructure setup

echo "=== Phase 1: Infrastructure Verification ==="
echo "Verifying Notification Router infrastructure..."

# Check Docker status
docker --version > /dev/null 2>&1
if [ $? -ne 0 ]; then
    echo "Error: Docker is not installed or not running"
    echo "Please install Docker Desktop and start it first"
    exit 1
fi

echo "🚀 Docker is running"

# Check PostgreSQL
echo "🗄️ Checking PostgreSQL..."
if docker ps | grep notification-router-postgres > /dev/null; then
    echo "✅ PostgreSQL is running"
    
    # Test PostgreSQL connection
    if docker exec notification-router-postgres pg_isready -U postgres -d notification_router > /dev/null 2>&1; then
        echo "✅ PostgreSQL is accessible"
    else
        echo "⚠️ PostgreSQL is running but not accessible"
    fi
else
    echo "❌ PostgreSQL is not running"
fi

# Check MongoDB
echo "📦 Checking MongoDB..."
if docker ps | grep notification-router-mongo > /dev/null; then
    echo "✅ MongoDB is running"
    
    # Test MongoDB connection
    if docker exec notification-router-mongo mongo --eval "db.adminCommand('ping')" > /dev/null 2>&1; then
        echo "✅ MongoDB is accessible"
    else
        echo "⚠️ MongoDB is running but not accessible"
    fi
else
    echo "❌ MongoDB is not running"
fi

# Check Novu
echo "🔥 Checking Novu..."
if docker ps | grep notification-router-novu > /dev/null; then
    echo "✅ Novu is running"
    
    # Test Novu API
    if curl -s http://localhost:3000/health > /dev/null 2>&1; then
        echo "✅ Novu API is accessible"
    else
        echo "⚠️ Novu is running but API is not accessible"
    fi
else
    echo "❌ Novu is not running"
fi

# Show network status
echo ""
echo "📡 Docker network status:"
docker network ls | grep notification-router-network || echo "Notification router network not found"

# Show running containers
echo ""
echo "📋 Container status:"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# Check if all services are running
echo ""
if docker ps | grep -E "(postgres|mongo|novu)" | wc -l | grep -q "3"; then
    echo "🎉 All infrastructure services are running!"
    echo ""
    echo "📚 Next steps:"
    echo "1. Update k8s/secrets.yaml with your Novu API Key"
    echo "2. Run Phase 2: Core API to start the application"
    echo ""
else
    echo "⚠️ Some infrastructure services are not running"
    echo "Please check the logs and restart the setup if needed"
fi