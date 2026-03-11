#!/bin/bash
# ============================================================
# Start ngrok tunnel for Buildkite webhook testing
# Exposes the local API (port 8082) to the internet so
# Buildkite can deliver webhooks to your dev environment.
#
# Usage: ./scripts/start-ngrok-buildkite.sh
#
# After running, copy the HTTPS URL and configure it in:
#   Buildkite → Settings → Notification Services → your webhook
#   URL: <ngrok-url>/api/v1/webhooks/buildkite
# ============================================================

set -e

API_PORT=8082

# Check ngrok is installed
if ! command -v ngrok &> /dev/null; then
    echo "ngrok is not installed. Install it with: brew install ngrok"
    echo "Then configure your auth token: ngrok config add-authtoken <token>"
    exit 1
fi

# Reuse existing tunnel if already running
EXISTING_URL=$(curl -s http://localhost:4040/api/tunnels 2>/dev/null \
    | grep -o '"public_url":"https://[^"]*' | head -1 | cut -d'"' -f4)

if [ -n "$EXISTING_URL" ]; then
    echo "ngrok already running: $EXISTING_URL"
else
    echo "Starting ngrok tunnel to port $API_PORT..."
    ngrok http $API_PORT --log=stdout > /tmp/ngrok-buildkite.log 2>&1 &
    NGROK_PID=$!
    sleep 4

    if ! kill -0 $NGROK_PID 2>/dev/null; then
        echo "ngrok failed to start. Check your auth token:"
        echo "  ngrok config add-authtoken <your-token>"
        tail -5 /tmp/ngrok-buildkite.log 2>/dev/null
        exit 1
    fi

    EXISTING_URL=$(curl -s http://localhost:4040/api/tunnels \
        | grep -o '"public_url":"https://[^"]*' | head -1 | cut -d'"' -f4)

    if [ -z "$EXISTING_URL" ]; then
        echo "ngrok started but no tunnel URL found. Try: ngrok http $API_PORT"
        kill $NGROK_PID 2>/dev/null
        exit 1
    fi
fi

WEBHOOK_URL="$EXISTING_URL/api/v1/webhooks/buildkite"

echo ""
echo "========================================"
echo "  ngrok tunnel active"
echo "========================================"
echo "  Public URL : $EXISTING_URL"
echo "  Webhook URL: $WEBHOOK_URL"
echo "  Dashboard  : http://localhost:4040"
echo "========================================"
echo ""
echo "Configure this webhook URL in Buildkite:"
echo "  Organisation → Settings → Notification Services → your webhook"
echo "  URL: $WEBHOOK_URL"
echo ""
echo "Make sure the API is running:"
echo "  make run-api   (or ./gradlew :api:bootRun)"
echo ""
