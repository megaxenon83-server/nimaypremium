#!/usr/bin/env bash
# Nimay OMP Bridge — shared configuration stage.
# Assumes bridge files already exist in /opt/nimay-omp-bridge.
# Writes config, installs the systemd unit, enables auto-start on boot.
#
# Usage: configure.sh [--user NAME] [--port N] [--voice] [--no-start]
#
# --print-user skriver bare ut brukernavnet som ville blitt brukt, og
# avslutter. Det brukes av install.sh --container, som maa vite samme
# bruker som den native installasjonen.
set -euo pipefail

INSTALL_DIR="/opt/nimay-omp-bridge"
UNIT_PATH="/etc/systemd/system/nimay-omp-bridge.service"
SERVICE="nimay-omp-bridge"
DEFAULT_PORT=7802

USER_NAME=""
PORT="$DEFAULT_PORT"
VOICE="false"
AUTO_START="true"
PRINT_USER="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --user)      USER_NAME="$2"; shift 2 ;;
    --port)      PORT="$2"; shift 2 ;;
    --voice)     VOICE="true"; shift ;;
    --no-start)  AUTO_START="false"; shift ;;
    --print-user) PRINT_USER="true"; shift ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

if [[ $EUID -ne 0 ]]; then
  echo "ERROR: configure.sh must run as root" >&2
  exit 1
fi

if [[ "$PRINT_USER" != "true" && ! -f "$INSTALL_DIR/omp-bridge.mjs" ]]; then
  echo "ERROR: $INSTALL_DIR/omp-bridge.mjs not found" >&2
  exit 1
fi

# --- resolve the service user -------------------------------------------------
# Explicit --user wins (root allowed if the admin insists). Otherwise SUDO_USER,
# otherwise the first regular human account.
if [[ -z "$USER_NAME" ]]; then
  if [[ -n "${SUDO_USER:-}" && "$SUDO_USER" != "root" ]]; then
    USER_NAME="$SUDO_USER"
  else
    USER_NAME="$(awk -F: '$3>=1000 && $3<65534 && $7 !~ /(nologin|false)/ {print $1; exit}' /etc/passwd)"
  fi
  if [[ -z "$USER_NAME" ]]; then
    echo "ERROR: could not determine a non-root user. Pass --user NAME" >&2
    exit 1
  fi
fi
if ! id "$USER_NAME" >/dev/null 2>&1; then
  echo "ERROR: user '$USER_NAME' does not exist" >&2
  exit 1
fi
USER_HOME="$(getent passwd "$USER_NAME" | cut -d: -f6)"
USER_GROUP="$(id -gn "$USER_NAME")"

if [[ "$PRINT_USER" == "true" ]]; then
  echo "$USER_NAME"
  exit 0
fi

echo "[info] service user: $USER_NAME ($USER_HOME), group: $USER_GROUP"

# --- locate node --------------------------------------------------------------
# Node maa vaere pa plass for at broen skal kunne starte. Hvis den mangler,
# installeres den her. Det er nodvendig for RPM-installasjonen, der
# configure.sh kjoeres fra %post og ingen annen installasjon har kjort.
NODE_BIN="$(command -v node || true)"
if [[ -z "$NODE_BIN" ]]; then
  echo "[info] node mangler - prøver å installere Node.js 20..."
  if command -v dnf >/dev/null 2>&1; then
    dnf install -y nodejs >/dev/null 2>&1 || true
  elif command -v apt-get >/dev/null 2>&1; then
    apt-get install -y nodejs >/dev/null 2>&1 || true
  elif command -v zypper >/dev/null 2>&1; then
    zypper --non-interactive install nodejs >/dev/null 2>&1 || true
  fi
  NODE_BIN="$(command -v node || true)"
fi
if [[ -z "$NODE_BIN" ]]; then
  echo "ERROR: node ble ikke funnet, og kunne ikke installeres." >&2
  echo "       Installer Node.js >= 18 og kjør denne skriptet på nytt:" >&2
  echo "         sudo dnf install nodejs   # Fedora" >&2
  echo "         sudo apt install nodejs   # Debian/Ubuntu" >&2
  exit 1
fi
NODE_MAJOR="$("$NODE_BIN" -e 'console.log(process.versions.node.split(".")[0])')"
if (( NODE_MAJOR < 18 )); then
  echo "ERROR: Node.js >= 18 required (found $("$NODE_BIN" --version))" >&2
  exit 1
fi
echo "[info] node: $NODE_BIN ($("$NODE_BIN" --version))"

# --- write config.json from the template --------------------------------------
TOKEN_FILE="$INSTALL_DIR/token"
if [[ -f "$TOKEN_FILE" ]]; then
  TOKEN="$(cat "$TOKEN_FILE")"
else
  TOKEN="$(head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n')"
  echo "$TOKEN" > "$TOKEN_FILE"
fi
chmod 600 "$TOKEN_FILE"

sed -e "s/__TOKEN__/${TOKEN}/" \
    -e "s/__PORT__/${PORT}/" \
    "$INSTALL_DIR/config.template.json" > "$INSTALL_DIR/config.json"
chmod 600 "$INSTALL_DIR/config.json"
echo "[info] config written (port ${PORT})"

# --- install systemd unit -----------------------------------------------------
sed -e "s|__USER__|${USER_NAME}|g" \
    -e "s|__GROUP__|${USER_GROUP}|g" \
    -e "s|__HOME__|${USER_HOME}|g" \
    -e "s|__NODE__|${NODE_BIN}|g" \
    "$INSTALL_DIR/nimay-omp-bridge.service" > "$UNIT_PATH"
chmod 644 "$UNIT_PATH"

# --- ownership: the bridge writes config.json / pid / meta next to itself -----
chown -R "${USER_NAME}:${USER_GROUP}" "$INSTALL_DIR"
chmod 755 "$INSTALL_DIR"
chmod 644 "$INSTALL_DIR/omp-bridge.mjs"

# --- pin the omp binary --------------------------------------------------------
# Uten omp-bin.txt faller broen tilbake pa ~/.bun/bin/omp.exe, som er en
# Windows-sti og aldri finnes pa Linux. Da feiler alle spawn-kall med ENOENT.
# write-omp-bin.sh maa kjores som tjenestebrukeren, ellers finner den
# ikke omp i brukerens ~/.local/bin.
if [[ -x "$INSTALL_DIR/scripts/write-omp-bin.sh" ]]; then
    # SERVICE_HOME settes til tjenestebrukerens home, slik at skriptet
    # vet hvor omp ligger, uansett hvem som kjoerer installasjonen.
    # Ikke la feilet omp-funn stoppe configure.sh: det er bedre aa fa
    # en installert bro + token, og gi brukeren beskjed om aa installere
    # omp etterpaa, enn aa avbryte med exit 1.
    if SERVICE_HOME="$USER_HOME" "$INSTALL_DIR/scripts/write-omp-bin.sh" "$INSTALL_DIR"; then
        echo "  omp-bin.txt:   skrevet"
    else
        echo "  ADVARSEL: fant ingen omp for $USER_NAME. Broen kan ikke starte omp." >&2
        echo "            Installer med: npm install -g --prefix ~/.local @oh-my-pi/pi-coding-agent" >&2
        echo "            og bun:    curl -fsSL https://bun.sh/install | bash" >&2
        echo "            Kjoer deretter: sudo /opt/nimay-omp-bridge/scripts/configure.sh" >&2
    fi
fi

# --- enable + start -----------------------------------------------------------
systemctl daemon-reload
systemctl enable "$SERVICE"
if [[ "$AUTO_START" == "true" ]]; then
  systemctl restart "$SERVICE"
  sleep 2
fi

# --- report -------------------------------------------------------------------
IP="$(hostname -I 2>/dev/null | awk '{print $1}' || true)"
IP="${IP:-127.0.0.1}"
echo
echo "================ Nimay OMP Bridge installert ================"
echo "  Autostart:     ja (systemd, starter ved oppstart)"
echo "  Tjeneste:      $SERVICE  (systemctl status $SERVICE)"
echo "  Logg:          journalctl -u $SERVICE -f"
echo "  Port:          $PORT"
echo "  Token:         $TOKEN"
echo "  Pairing:       omp2://${IP}:${PORT}"
echo "  Config:        $INSTALL_DIR/config.json"
echo "============================================================="
