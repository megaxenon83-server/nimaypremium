#!/usr/bin/env bash
# Nimay OMP Bridge — uninstaller.
set -euo pipefail

SERVICE="nimay-omp-bridge"
INSTALL_DIR="/opt/nimay-omp-bridge"
UNIT_PATH="/etc/systemd/system/nimay-omp-bridge.service"

if [[ $EUID -ne 0 ]]; then
  echo "ERROR: run as root (sudo $0)" >&2
  exit 1
fi

systemctl stop "$SERVICE" 2>/dev/null || true
systemctl disable "$SERVICE" 2>/dev/null || true
rm -f "$UNIT_PATH"
systemctl daemon-reload
rm -rf "$INSTALL_DIR"
echo "[ok] Nimay OMP Bridge fjernet."
