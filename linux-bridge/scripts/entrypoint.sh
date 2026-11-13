#!/bin/bash
# Entry point for Nimay OMP Bridge i containeren.
#
# Genererer config.json ved oppstart hvis den mangler, og starter deretter
# broen med de argumentene den ble kalt med.
#
# Broen leser config.json fra /opt/nimay-omp-bridge (HERE). Filen er
# bind-mountet fra verten, slik at tokenet overlever omstarter og
# paringen i appen gaar ikke tapt.
set -e

BRIDGE_DIR="/opt/nimay-omp-bridge"
CONFIG="$BRIDGE_DIR/config.json"
TEMPLATE="$BRIDGE_DIR/config.template.json"

# Malen maa vaere tilgjengelig. Hvis config.json er en bind-mount av en
# fil, ligger malen i image-et og er derfor tilgjengelig her.
if [ ! -f "$TEMPLATE" ] && [ -f "$(dirname "$CONFIG")/config.template.json" ]; then
  cp "$(dirname "$CONFIG")/config.template.json" "$TEMPLATE" 2>/dev/null || true
fi

# Er config.json tom (mountet fra en tom fil), skriver vi den.
if [ ! -s "$CONFIG" ] && [ -f "$TEMPLATE" ]; then
  PORT="${OMP_PORT:-7802}"
  TOKEN="$(head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n')"
  sed -e "s/__PORT__/$PORT/" -e "s/__TOKEN__/$TOKEN/" "$TEMPLATE" > "$CONFIG"
  chmod 600 "$CONFIG" 2>/dev/null || true
  echo "[bridge] genererte config.json med nytt token (port $PORT)"
fi

exec "$@"
