#!/bin/bash
# ============================================================
# Start Buildkite agent for local webhook testing
#
# Usage: ./scripts/start-buildkite-agent.sh [queue]
#
# Default queue: default-self-hosted
# The queue must exist in Buildkite → your org → Settings →
#   Clusters → Default cluster → Queues
#
# Prerequisites:
#   brew install buildkite/buildkite/buildkite-agent
#   Configure token in /usr/local/etc/buildkite-agent/buildkite-agent.cfg
# ============================================================

QUEUE="${1:-default-self-hosted}"

# Check agent is installed
if ! command -v buildkite-agent &> /dev/null; then
    echo "buildkite-agent is not installed."
    echo ""
    echo "Install with:"
    echo "  brew tap buildkite/buildkite"
    echo "  brew install buildkite-agent"
    echo ""
    echo "Then configure your agent token:"
    echo "  /usr/local/etc/buildkite-agent/buildkite-agent.cfg"
    echo "    token=\"<your-agent-token>\""
    exit 1
fi

echo "========================================"
echo "  Starting Buildkite agent"
echo "  Queue: $QUEUE"
echo "========================================"
echo ""
echo "Events this agent will trigger:"
echo "  agent.connected, agent.disconnected"
echo "  build.running, build.finished"
echo "  job.scheduled, job.started, job.finished"
echo ""
echo "Press Ctrl+C to stop the agent."
echo ""

buildkite-agent start --queue "$QUEUE"
