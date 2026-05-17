#!/usr/bin/env bash
# Installs minikube-tunnel as a systemd service for whoever runs this script.
# Run once after cloning: bash k8s/install-tunnel.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SERVICE_SRC="$SCRIPT_DIR/minikube-tunnel.service"
SERVICE_DST="/etc/systemd/system/minikube-tunnel.service"

sed "s|__CURRENT_USER__|$(whoami)|g" "$SERVICE_SRC" | sudo tee "$SERVICE_DST" > /dev/null

sudo systemctl daemon-reload
sudo systemctl enable minikube-tunnel
sudo systemctl start minikube-tunnel

echo "minikube tunnel is now running and will auto-start on boot."
echo "Test with: curl -s -o /dev/null -w '%{http_code}' http://localhost:8081/api/users"
