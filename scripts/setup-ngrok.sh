#!/bin/bash
# ============================================================
# Setup ngrok tunnel for Slack OAuth (local development)
# Usage: ./scripts/setup-ngrok.sh
# ============================================================

set -e

if ! command -v ngrok &> /dev/null; then
    echo "ngrok is not installed. Install with: brew install ngrok"
    echo "Then configure: ngrok config add-authtoken <your-token>"
    exit 1
fi

# Check if ngrok is already running
EXISTING_URL=$(curl -s http://localhost:4040/api/tunnels 2>/dev/null | grep -o '"public_url":"https://[^"]*' | head -1 | cut -d'"' -f4)

if [ -n "$EXISTING_URL" ]; then
    NGROK_URL="$EXISTING_URL"
    echo "ngrok already running: $NGROK_URL"
else
    echo "Starting ngrok tunnel to port 8082..."
    ngrok http 8082 --log=stdout > /dev/null &
    sleep 3

    NGROK_URL=$(curl -s http://localhost:4040/api/tunnels | grep -o '"public_url":"https://[^"]*' | head -1 | cut -d'"' -f4)

    if [ -z "$NGROK_URL" ]; then
        echo "Failed to get ngrok URL. Check: ngrok config add-authtoken <your-token>"
        exit 1
    fi
fi

# Write to .env
if [ -f .env ] && grep -q "^NGROK_URL=" .env; then
    sed -i.bak "s|^NGROK_URL=.*|NGROK_URL=$NGROK_URL|" .env && rm -f .env.bak
    echo "Updated NGROK_URL in .env"
else
    echo "" >> .env
    echo "NGROK_URL=$NGROK_URL" >> .env
    echo "Added NGROK_URL to .env"
fi

echo ""
echo "ngrok URL:  $NGROK_URL"
echo "Dashboard:  $NGROK_URL/dashboard.html"
echo "Admin:      $NGROK_URL/index.html"
echo "OAuth callback: $NGROK_URL/auth/slack/callback"
echo ""
echo "Add this redirect URL in your Slack app (one-time):"
echo "  https://api.slack.com/apps -> OAuth & Permissions -> Redirect URLs"
echo "  $NGROK_URL/auth/slack/callback"
