#!/bin/bash
# ============================================================
# Setup ngrok tunnel for Slack OAuth (local development)
# Usage: ./scripts/setup-ngrok.sh
# ============================================================

set -e

# ----------------------------------------------------------
# Step 1: Check ngrok is installed
# ----------------------------------------------------------
if ! command -v ngrok &> /dev/null; then
    echo "ngrok is not installed."
    echo ""
    echo "To install ngrok:"
    echo "  macOS:   brew install ngrok"
    echo "  Linux:   https://ngrok.com/download"
    echo "  Windows: choco install ngrok"
    echo ""
    echo "After installing, you need to configure your auth token:"
    echo "  1. Create a free account at https://dashboard.ngrok.com/signup"
    echo "  2. Copy your auth token from https://dashboard.ngrok.com/get-started/your-authtoken"
    echo "  3. Run: ngrok config add-authtoken <your-token>"
    echo ""
    echo "Then re-run this script."
    exit 1
fi

# ----------------------------------------------------------
# Step 2: Check ngrok auth token is configured
# ----------------------------------------------------------
# Try starting ngrok and check if it fails due to missing auth
EXISTING_URL=$(curl -s http://localhost:4040/api/tunnels 2>/dev/null | grep -o '"public_url":"https://[^"]*' | head -1 | cut -d'"' -f4)

if [ -n "$EXISTING_URL" ]; then
    NGROK_URL="$EXISTING_URL"
    echo "ngrok already running: $NGROK_URL"
else
    echo "Starting ngrok tunnel to port 8082..."
    ngrok http 8082 --log=stdout > /tmp/ngrok-setup.log 2>&1 &
    NGROK_PID=$!
    sleep 4

    # Check if ngrok is still running
    if ! kill -0 $NGROK_PID 2>/dev/null; then
        echo "ngrok failed to start. This usually means your auth token is not configured."
        echo ""
        echo "To fix this:"
        echo "  1. Go to https://dashboard.ngrok.com/get-started/your-authtoken"
        echo "  2. Copy your auth token"
        echo "  3. Run: ngrok config add-authtoken <your-token>"
        echo ""
        echo "ngrok log output:"
        tail -5 /tmp/ngrok-setup.log 2>/dev/null
        echo ""
        echo "Then re-run this script."
        exit 1
    fi

    NGROK_URL=$(curl -s http://localhost:4040/api/tunnels | grep -o '"public_url":"https://[^"]*' | head -1 | cut -d'"' -f4)

    if [ -z "$NGROK_URL" ]; then
        echo "ngrok started but no tunnel URL found."
        echo ""
        echo "This can happen if:"
        echo "  - Your auth token is invalid or expired"
        echo "  - You've hit the free plan tunnel limit"
        echo "  - Port 8082 is not available"
        echo ""
        echo "Try running manually to see the error: ngrok http 8082"
        kill $NGROK_PID 2>/dev/null
        exit 1
    fi
fi

# Update application-local.yml/.yaml with the ngrok URL
LOCAL_YML="api/src/main/resources/application-local.yml"
LOCAL_YAML="api/src/main/resources/application-local.yaml"
if [ -f "$LOCAL_YAML" ]; then
    LOCAL_YML="$LOCAL_YAML"
fi

if [ -f "$LOCAL_YML" ]; then
    if grep -q "redirect-uri:" "$LOCAL_YML"; then
        sed -i.bak "s|redirect-uri:.*|redirect-uri: $NGROK_URL/auth/slack/callback|" "$LOCAL_YML" && rm -f "$LOCAL_YML.bak"
        echo "Updated redirect-uri in $LOCAL_YML"
    else
        echo "" >> "$LOCAL_YML"
        echo "app:" >> "$LOCAL_YML"
        echo "  slack:" >> "$LOCAL_YML"
        echo "    oauth:" >> "$LOCAL_YML"
        echo "      redirect-uri: $NGROK_URL/auth/slack/callback" >> "$LOCAL_YML"
        echo "Added redirect-uri block to $LOCAL_YML"
    fi
else
    echo "Warning: No application-local.yml or .yaml found. Create it or set the redirect URI manually."
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
echo ""
echo "Now start the API:  ./gradlew :api:bootRun --no-daemon"
