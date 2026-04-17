#!/usr/bin/env bash
set -euo pipefail

SERVER="root@62.171.153.133"
REPO="https://github.com/KrErte/CAD.git"
APP_DIR="/root/CAD"

echo "=== Deploying AI-CAD to $SERVER ==="

ssh "$SERVER" bash -s << 'REMOTE'
set -euo pipefail

# Install Docker if missing
if ! command -v docker &>/dev/null; then
  echo ">>> Installing Docker..."
  apt-get update -qq
  apt-get install -y -qq docker.io docker-compose-plugin git curl
  systemctl enable --now docker
else
  echo ">>> Docker already installed"
fi

# Clone or pull repo
if [ -d /root/CAD ]; then
  echo ">>> Pulling latest code..."
  cd /root/CAD && git pull
else
  echo ">>> Cloning repo..."
  git clone https://github.com/KrErte/CAD.git /root/CAD
fi

cd /root/CAD

# Write .env
cat > .env << 'ENV'
ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY:?Set ANTHROPIC_API_KEY before running}
CLAUDE_MODEL=claude-opus-4-6
MESHY_API_KEY=
ENV

# Build and start
echo ">>> Building and starting containers..."
docker compose down --remove-orphans 2>/dev/null || true
docker compose up --build -d

echo ">>> Waiting for services..."
sleep 10
docker compose ps

echo ""
echo "=== Deploy complete! ==="
echo "Frontend: http://62.171.153.133:4200"
echo "Backend:  http://62.171.153.133:8080"
echo "Worker:   http://62.171.153.133:8000"
REMOTE
