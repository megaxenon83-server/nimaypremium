#!/usr/bin/env bash
# Nimay OMP Bridge - installasjon for Bazzite / Silverblue / uBlue / uCore
#
# Enkeltstegs-installasjon for immutable OS. Henter det ferdigbygde
# container-imaget fra GitHub Container Registry, registrerer Quadlet-
# enheten og starter tjenesten.
#
#   curl -fsSL https://raw.githubusercontent.com/megaxenon83-server/OmpChat/main/linux-bridge/install-bazzite.sh | sudo bash
#
# Alternativt, last ned filen og kjoer:
#   chmod +x install-bazzite.sh && sudo ./install-bazzite.sh
#
# Valgfrie argumenter:
#   --user NAME      Brukeren tjenesten skal kjoere som (standard: SUDO_USER)
#   --port N         Port broen lytter pa (standard: 7802)
#   --local          Bygg image-et lokalt i stedet for aa hente det

set -euo pipefail

IMAGE="ghcr.io/megaxenon83-server/ompchat/omp-bridge:latest"
INSTALL_DIR="/opt/nimay-omp-bridge"
CONTAINER_DIR="/etc/containers/systemd"
# Quadlet-enheten maa ha et annet navn enn den native
# nimay-omp-bridge.service. Ellers vil systemd bruke den gamle enheten,
# som kjoerer node mot en container-mount og feiler med CHDIR.
UNIT_NAME="nimay-omp-bridge-container"
UNIT="$CONTAINER_DIR/$UNIT_NAME.container"
PORT="7802"
USER_NAME=""
USE_LOCAL="false"

# Argumenter parses med while, ikke for. "shift" i en for-løkke flytter
# ikke posisjonen i "$@", slik at verdien etter --user ville blitt
# behandlet som et eget argument.
while [[ $# -gt 0 ]]; do
  case "$1" in
    --user) USER_NAME="${2:-}"; shift 2 ;;
    --port) PORT="${2:-}"; shift 2 ;;
    --local) USE_LOCAL="true"; shift ;;
    *) echo "Ukjent argument: $1" >&2; exit 2 ;;
  esac
done

if [[ $EUID -ne 0 ]]; then
  echo "ERROR: kjoer som root (sudo $0)" >&2
  exit 1
fi

if ! command -v podman >/dev/null 2>&1; then
  echo "ERROR: podman er ikke installert." >&2
  echo "       Pa Bazzite/Silverblue er podman standard. Installere med:" >&2
  echo "       sudo rpm-ostree install podman" >&2
  exit 1
fi

if ! command -v systemctl >/dev/null 2>&1; then
  echo "ERROR: systemd er ikke tilgjengelig. Quadlet krever systemd." >&2
  exit 1
fi

# --- Bruker ------------------------------------------------------------------
if [[ -z "$USER_NAME" ]]; then
  USER_NAME="${SUDO_USER:-}"
fi
if [[ -z "$USER_NAME" ]]; then
  USER_NAME="$(awk -F: '$3>=1000 && $3<65534 && $7 !~ /(nologin|false)/ {print $1; exit}' /etc/passwd)"
fi
if [[ -z "$USER_NAME" ]]; then
  echo "ERROR: fant ingen vanlig bruker. Bruk --user NAME." >&2
  exit 1
fi
if ! id "$USER_NAME" >/dev/null 2>&1; then
  echo "ERROR: brukeren '$USER_NAME' finnes ikke." >&2
  exit 1
fi
USER_HOME="$(getent passwd "$USER_NAME" | cut -d: -f6)"

echo "[info] bruker:      $USER_NAME ($USER_HOME)"
echo "[info] port:        $PORT"
echo "[info] image:       $IMAGE"

# --- Container-image ----------------------------------------------------------
if [[ "$USE_LOCAL" == "true" ]]; then
  echo "[info] bygger image-et lokalt..."
  SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  if [[ ! -f "$SCRIPT_DIR/Containerfile" ]]; then
    echo "ERROR: Containerfile finnes ikke ved siden av skriptet." >&2
    echo "       Bruk standardinstallasjonen, eller last ned hele linux-bridge-mappen." >&2
    exit 1
  fi
  podman build -t "$IMAGE" -f "$SCRIPT_DIR/Containerfile" "$SCRIPT_DIR"
else
  echo "[info] henter image-et..."
  if ! podman pull "$IMAGE"; then
    echo "[info] kunne ikke hentes, bygger lokalt er ikke mulig uten Containerfile." >&2
    echo "       Last ned linux-bridge-mappen fra repoet, og kjoer ./install.sh --container" >&2
    exit 1
  fi
fi

# --- Tilstandsmappe for OMP --------------------------------------------------
# ~/.omp maa vaere eid av UID 1000, ellers kan ikke broen skrive
# agent-databasen der.
#
# Stien maa ogsa vaere tilgjengelig for UID 1000. /home/<bruker> er ofte
# eid av root uten traverse-rettighet, saa bind-mounten legges i /var/lib
# der alle kan naa.
OMP_STATE_DIR="/var/lib/nimay-omp-bridge/omp"
mkdir -p "$OMP_STATE_DIR"
if [[ -d "$USER_HOME/.omp" ]]; then
  # Ta med eksisterende auth og modellkonfigurasjon fra brukerens ~/.omp
  cp -a "$USER_HOME/.omp/." "$OMP_STATE_DIR/" 2>/dev/null || true
fi
chown -R 1000:1000 "$OMP_STATE_DIR"
echo "[info] tilstand: $OMP_STATE_DIR (eies av UID 1000)"

# Config-mappen pa verten. Tokenet ligger her, saa det overlever
# omstarter og paringen i appen gaar ikke tapt.
#
# Malen hentes fra image-et, saa verten og containeren bruker samme
# oppsett.
CONFIG_DIR="/var/lib/nimay-omp-bridge/config"
mkdir -p "$CONFIG_DIR"
if [ ! -f "$CONFIG_DIR/config.template.json" ]; then
  podman run --rm --entrypoint cat "$IMAGE" /opt/nimay-omp-bridge/config.template.json \
    > "$CONFIG_DIR/config.template.json" 2>/dev/null || true
fi
# Filen som mountes maa finnes pa verten for containeren skal kunne starte.
# Podman feiler med "statfs ... no such file or directory" ellers.
# Tom fil er nok: entrypointen genererer den ekte configen ved oppstart.
[ -f "$CONFIG_DIR/config.json" ] || : > "$CONFIG_DIR/config.json"
chown -R 1000:1000 "$CONFIG_DIR"
echo "[info] config: $CONFIG_DIR (tokenet overlever omstart)"

# --- Quadlet-enhet ------------------------------------------------------------
mkdir -p "$CONTAINER_DIR"

cat > "$UNIT" <<EOF
[Unit]
Description=Nimay OMP Bridge
Documentation=https://chat.nimay.net/nimayopp/
After=network-online.target
Wants=network-online.target

[Container]
Image=$IMAGE

# Kjoer som UID 1000 for aa matche omp-brukeren i image-et. Som
# vertsbrukeren kan ikke containeren skrive til ~/.omp (EACCES).
User=1000
Group=1000

Environment=HOME=/home/omp
Environment=OMP_HOME=/home/omp/.omp
Environment=OMP_PORT=$PORT
Environment=PATH=/home/omp/.local/bin:/home/omp/.bun/bin:/usr/local/bin:/usr/bin:/bin

# ~/.omp maa eies av UID 1000, ellers kan ikke broen skrive
# agent-databasen der. Se OMP_STATE_DIR over for hvor den ligger.
Volume=$OMP_STATE_DIR:/home/omp/.omp:Z

# Config.json (token og port) mountes fra verten, saa tokenet overlever
# omstarter. Mounten maa dekke config.json direkte, siden broen leser
# den fra /opt/nimay-omp-bridge.
# (Hele /opt/nimay-omp-bridge kan ikke mountes - det ville skjult
# bro-fila i image-et.)
Volume=$CONFIG_DIR/config.json:/opt/nimay-omp-bridge/config.json:Z

# Brukerens hjemmekatalog maa mountes inn, ellers finner broen ingen
# prosjektmapper a jobbe i (og /home/<bruker> er ikke tilgjengelig
# for UID 1000 i containeren).
Volume=$USER_HOME:/home/user:Z
Network=host

AutoUpdate=registry

[Install]
WantedBy=multi-user.target

[Service]
Restart=always
RestartSec=3
EOF

echo "[info] skrev $UNIT"

# UID 1000 maa ha traverse-rettighet gjennom /home/<bruker> for aa na
# prosjektmappene i containeren. Uten det feiler broen med
# "EACCES: permission denied, stat '/home/user/Documents'".
chmod o+x "$USER_HOME" 2>/dev/null || true

# Config.json genereres i containeren ved oppstart, fordi
# /opt/nimay-omp-bridge ikke skal mountes fra verten: en mount der ville
# skjult bro-fila i image-et, og node ville feilet med MODULE_NOT_FOUND.

# --- ~/.omp med riktig eierskap ----------------------------------------------
# OMP lagrer autentiseringen i ~/.omp/agent/agent.db og modellkonfigurasjonen
# i ~/.omp/agent/models.yml. Hele mappa maa mountes, ellers får broen
# "No models available" og svarer ikke.
mkdir -p "$USER_HOME/.omp"
chown -R 1000:1000 "$USER_HOME/.omp"
echo "[info] ~/.omp eies av UID 1000"

if [[ -d "$USER_HOME/.omp/agent" ]]; then
  if [[ -f "$USER_HOME/.omp/agent/models.yml" ]]; then
    echo "[info] fant models.yml - broen får modellkonfigurasjon"
  else
    echo "[info] ADVARSEL: ~/.omp/agent/models.yml mangler. Sett opp modellkonfigurasjon i OMP pa PC-en." >&2
  fi
else
  echo "[info] ADVARSEL: ~/.omp/agent mangler. Sett opp modellkonfigurasjon i OMP pa PC-en." >&2
fi

# --- Start --------------------------------------------------------------------
# Den native tjenesten kjører node mot /opt/nimay-omp-bridge, som i
# container-varianten er en mount, ikke en mappe med node-filer. Den
# maskes derfor bort, slik at den ikke kolliderer med Quadlet-enheten.
if systemctl is-active --quiet nimay-omp-bridge 2>/dev/null; then
  echo "[info] stopper den native tjenesten (den holder porten)"
  systemctl stop nimay-omp-bridge 2>/dev/null || true
fi
if systemctl list-unit-files 2>/dev/null | grep -q "^nimay-omp-bridge.service"; then
  systemctl mask nimay-omp-bridge 2>/dev/null || true
  echo "[info] masker den native tjenesten (kolliderer med Quadlet-enheten)"
fi

systemctl daemon-reload

# Quadlet genererer enheten i /run/systemd/generator, og slike
# "transient or generated" enheter kan ikke enable's. De startes med
# "systemctl start" og settes til a starte ved boot med "enable".
# (systemctl enable fungerer for Quadlet-enheter, men ikke naar
# enheten allerede er generert i denne kjoringen.)
systemctl enable "$UNIT_NAME" 2>/dev/null || true
systemctl restart "$UNIT_NAME" 2>/dev/null || systemctl start "$UNIT_NAME"

sleep 3

if ! systemctl is-active --quiet "$UNIT_NAME"; then
  echo ""
  echo "ERROR: tjenesten startet ikke. Se loggene:" >&2
  echo "       journalctl -u $UNIT_NAME -n 40" >&2
  exit 1
fi

# --- Token -------------------------------------------------------------------
# Tokenet leses fra containeren. Quadlet gir containeren navnet
# "systemd-<enhetsnavn>", saa begge varianter sjekkes.
TOKEN=""
for candidate in "$UNIT_NAME" "systemd-$UNIT_NAME"; do
  TOKEN="$(podman exec "$candidate" node -e \
    'console.log(JSON.parse(require("fs").readFileSync("/opt/nimay-omp-bridge/config.json","utf8")).token)' 2>/dev/null || true)"
  [[ -n "$TOKEN" ]] && break
done

IP="$(hostname -I 2>/dev/null | awk '{print $1}')"
IP="${IP:-127.0.0.1}"

echo ""
echo "================ Nimay OMP Bridge installert ================"
echo "  Tjeneste:      $UNIT_NAME (systemctl status $UNIT_NAME)"
echo "  Port:          $PORT"
echo "  Bruker:        $USER_NAME"
if [[ -n "$TOKEN" ]]; then
  echo "  Token:         $TOKEN"
  echo "  Pairing:       omp2://$IP:$PORT?token=$TOKEN"
fi
echo "============================================================="
echo ""
echo "Koble til fra appen med ovenstaaende paringsadresse."
echo ""
