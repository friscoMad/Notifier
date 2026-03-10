#!/bin/bash

# Phase 1: Infrastructure Setup
# This script sets up the Notification Router infrastructure for local development

echo "=== Phase 1: Infrastructure Setup ==="
echo "Setting up Notification Router infrastructure..."

# Check if Docker is running
docker --version > /dev/null 2>&1
if [ $? -ne 0 ]; then
    echo "Error: Docker is not installed or not running"
    echo "Please install Docker Desktop and start it first"
    exit 1
fi

echo "🚀 Docker is running"

# Create network for local development
echo "📡 Creating Docker network..."
docker network create notification-router-network 2>/dev/null || echo "Network already exists"

# Start PostgreSQL
echo "🗄️ Starting PostgreSQL..."
docker run -d \
    --name notification-router-postgres \
    --network notification-router-network \
    --env POSTGRES_PASSWORD=postgres \
    --env POSTGRES_DB=notification_router \
    --publish 5432:5432 \
    --volume postgres-data:/var/lib/postgresql/data \
    postgres:15

# Wait for PostgreSQL to be ready
echo "⏳ Waiting for PostgreSQL to be ready..."
sleep 10

# Check if PostgreSQL is running
docker ps | grep notification-router-postgres > /dev/null
if [ $? -eq 0 ]; then
    echo "✅ PostgreSQL is running"
else
    echo "❌ Failed to start PostgreSQL"
    exit 1
fi

# Install Novu using Docker (minimal setup)
echo "🔥 Installing Novu..."
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

# Start MongoDB for Novu (required)
echo "📦 Starting MongoDB..."
docker run -d \
    --name notification-router-mongo \
    --network notification-router-network \
    --publish 27017:27017 \
    --volume mongo-data:/data/db \
    mongo:6

# Wait for Novu to be ready
echo "⏳ Waiting for Novu to be ready..."
sleep 30

# Check if Novu is running
docker ps | grep notification-router-novu > /dev/null
if [ $? -eq 0 ]; then
    echo "✅ Novu is running"
else
    echo "❌ Failed to start Novu"
    exit 1
fi

# Show running containers
echo ""
echo "📋 Running containers:"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# Get Novu API Key
echo ""
echo "🔑 To get your Novu API Key:"
echo "1. Open http://localhost:3000 in your browser"
echo "2. Sign up for a new account"
echo "3. Go to Settings → API Keys"
echo "4. Copy the API Key and add it to k8s/secrets.yaml"
echo ""
echo "📚 Next steps:"
echo "- Update k8s/secrets.yaml with your Novu API Key"
echo "- Run Phase 2: Core API to start the application"
echo ""
echo "✅ Phase 1: Infrastructure Setup completed successfully!"