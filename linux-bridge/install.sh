#!/usr/bin/env bash
# Nimay OMP Bridge — standalone installer for any Linux distro.
# Usage: sudo ./install.sh [--user NAME] [--port N] [--voice]
#
# For immutable OS (Bazzite, Silverblue, uBlue, uCore) brukes
# Quadlet-varianten, som kjører broen i en Podman-container:
#   sudo ./install.sh --container
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INSTALL_DIR="/opt/nimay-omp-bridge"
PAYLOAD="$HERE/payload"

USE_CONTAINER=false
for arg in "$@"; do
  [[ "$arg" == "--container" ]] && USE_CONTAINER=true
done

if [[ $EUID -ne 0 ]]; then
  echo "ERROR: run as root (sudo $0)" >&2
  exit 1
fi

# --- ensure Node.js >= 18 -----------------------------------------------------
need_node=true
if command -v node >/dev/null 2>&1; then
  major="$(node -e 'console.log(process.versions.node.split(".")[0])')"
  (( major >= 18 )) && need_node=false
fi

if [[ "$need_node" == "true" ]]; then
  echo "[info] installing Node.js 20..."
  if command -v apt-get >/dev/null 2>&1; then
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
    apt-get install -y nodejs
  elif command -v dnf >/dev/null 2>&1; then
    curl -fsSL https://rpm.nodesource.com/setup_20.x | bash -
    dnf install -y nodejs
  else
    echo "ERROR: unsupported distro; install Node.js >= 18 manually" >&2
    exit 1
  fi
fi

# --- copy payload -------------------------------------------------------------
echo "[info] installing files to $INSTALL_DIR"
mkdir -p "$INSTALL_DIR"
cp -r "$PAYLOAD/bridge/." "$INSTALL_DIR/"
cp -r "$PAYLOAD/tools" "$INSTALL_DIR/" 2>/dev/null || true
cp -r "$PAYLOAD/voice" "$INSTALL_DIR/" 2>/dev/null || true
cp "$PAYLOAD/nimay-omp-bridge.service" "$INSTALL_DIR/"
# configure.sh trenger write-omp-bin.sh ved siden av seg for aa kunne
# peke broen pa riktig omp-binary.
mkdir -p "$INSTALL_DIR/scripts"
cp -r "$HERE/scripts/." "$INSTALL_DIR/scripts/" 2>/dev/null || true

# --- configure + enable -------------------------------------------------------
if [[ "$USE_CONTAINER" == "true" ]]; then
  # Quadlet-varianten for immutable OS (Bazzite, Silverblue, uBlue, uCore).
  if ! command -v podman >/dev/null 2>&1; then
    echo "ERROR: podman er påkrevd for --container" >&2
    exit 1
  fi
  echo "[info] henter container-image..."
  # Det publiserte image-et hentes narere. Kun hvis det ikke finnes,
  # bygges det lokalt fra Containerfile.
  if ! podman pull ghcr.io/megaxenon83-server/ompchat/omp-bridge:latest 2>/dev/null; then
    echo "[info] publisert image kunne ikke hentes, bygger lokalt..."
    podman build -t ghcr.io/megaxenon83-server/ompchat/omp-bridge:latest -f "$HERE/Containerfile" "$HERE"
  else
    echo "[info] hentet ghcr.io/megaxenon83-server/ompchat/omp-bridge:latest"
  fi

  CONTAINER_DIR="/etc/containers/systemd"
  # Quadlet-enheten maa ha et eget navn. Ellers bruker systemd den gamle
  # native enheten, som kjoerer node mot /opt/nimay-omp-bridge - en
  # container-mount, ikke en mappe med node-filer.
  UNIT_NAME="nimay-omp-bridge-container"
  mkdir -p "$CONTAINER_DIR"
  cp "$HERE/nimay-omp-bridge-container.container" "$CONTAINER_DIR/"

  # Brukernavn bestemmes av configure.sh, slik at native og container
  # installasjon velger samme bruker.
  CONTAINER_USER="$(bash "$HERE/scripts/configure.sh" --print-user "$@" 2>/dev/null || true)"
  if [[ -z "$CONTAINER_USER" ]]; then
    CONTAINER_USER="${SUDO_USER:-$(awk -F: '$3>=1000 && $3<65534 && $7 !~ /(nologin|false)/ {print $1; exit}' /etc/passwd)}"
  fi
  CONTAINER_HOME="$(getent passwd "$CONTAINER_USER" | cut -d: -f6)"
  OMP_STATE_DIR="/var/lib/nimay-omp-bridge/omp"
  CONFIG_DIR="/var/lib/nimay-omp-bridge/config"

  # ~/.omp maa vaere eid av UID 1000, ellers kan ikke broen skrive
  # agent-databasen der (EACCES ved mkdir).
  #
  # Stien maa ogsa vaere tilgjengelig for UID 1000. /home/<bruker> er ofte
  # eid av root uten traverse-rettighet, saa bind-mounten legges i /var/lib
  # der alle kan naa.
  mkdir -p "$OMP_STATE_DIR"
  if [[ -d "$CONTAINER_HOME/.omp" ]]; then
    cp -a "$CONTAINER_HOME/.omp/." "$OMP_STATE_DIR/" 2>/dev/null || true
  fi
  chown -R 1000:1000 "$OMP_STATE_DIR"
  echo "[info] tilstand: $OMP_STATE_DIR (eies av UID 1000)"

  # Config-mappen pa verten. Tokenet ligger her, saa det overlever
  # omstarter og paringen i appen gaar ikke tapt.
  mkdir -p "$CONFIG_DIR"
  if [ ! -f "$CONFIG_DIR/config.template.json" ]; then
    podman run --rm --entrypoint cat ghcr.io/megaxenon83-server/ompchat/omp-bridge:latest \
      /opt/nimay-omp-bridge/config.template.json > "$CONFIG_DIR/config.template.json" 2>/dev/null || true
  fi
  # Filen som mountes maa finnes pa verten, ellers feiler podman med
  # "statfs ... no such file or directory". Tom fil er nok: entrypointen
  # genererer den ekte configen ved oppstart.
  [ -f "$CONFIG_DIR/config.json" ] || : > "$CONFIG_DIR/config.json"
  chown -R 1000:1000 "$CONFIG_DIR"
  echo "[info] config: $CONFIG_DIR (tokenet overlever omstart)"

  # Quadlet-enheten far alle stier. Skriv variabler FØR sed, ellers dør
  # skriptet med "unbound variable" fordi set -u er paa.
  sed -i "s|<BRUKER>|$CONTAINER_USER|g" "$CONTAINER_DIR/$UNIT_NAME.container"
  sed -i "s|<OMP_STATE>|$OMP_STATE_DIR|g" "$CONTAINER_DIR/$UNIT_NAME.container"
  sed -i "s|<USER_HOME>|$CONTAINER_HOME|g" "$CONTAINER_DIR/$UNIT_NAME.container"
  sed -i "s|<CONFIG_DIR>|$CONFIG_DIR|g" "$CONTAINER_DIR/$UNIT_NAME.container"

  # /opt/nimay-omp-bridge mountes inn i containeren, og podman krever at
  # mappen finnes pa verten. Uten den feiler tjenesten med
  # "statfs /opt/nimay-omp-bridge: no such file or directory".
  mkdir -p "$INSTALL_DIR"
  chown -R 1000:1000 "$INSTALL_DIR" 2>/dev/null || true

  # Den native tjenesten holder port 7802, og containeren kan ikke starte
  # mens den kjører. Den maskes ogsa bort, slik at den ikke kolliderer
  # med Quadlet-enheten.
  if systemctl is-active --quiet nimay-omp-bridge 2>/dev/null; then
    echo "[info] stopper den native tjenesten (den holder port 7802)"
    systemctl stop nimay-omp-bridge 2>/dev/null || true
  fi
  if systemctl list-unit-files 2>/dev/null | grep -q "^nimay-omp-bridge.service"; then
    systemctl mask nimay-omp-bridge 2>/dev/null || true
    echo "[info] maskerer den native tjenesten (kolliderer med Quadlet-enheten)"
  fi

  # UID 1000 maa ha traverse-rettighet gjennom /home/<bruker> for aa na
  # prosjektmappene i containeren.
  chmod o+x "$CONTAINER_HOME" 2>/dev/null || true

  systemctl daemon-reload
  # Quadlet-enheter er "transient or generated" og kan ikke enable's
  # mens de allerede er generert. De startes med start/restart, og
  # settes til a starte ved boot med enable.
  systemctl enable "$UNIT_NAME" 2>/dev/null || true
  systemctl restart "$UNIT_NAME" 2>/dev/null || systemctl start "$UNIT_NAME"
  echo
  echo "================ Nimay OMP Bridge (container) ================"
  echo "  Tjeneste:      $UNIT_NAME (systemctl status $UNIT_NAME)"
  echo "  Image:         ghcr.io/megaxenon83-server/ompchat/omp-bridge:latest"
  echo "  Bruker:        $CONTAINER_USER"
  echo "  Port:          7802"
  echo
  echo "  Token:"
  # Tokenet leses fra containeren. Quadlet gir containeren navnet
  # "systemd-<enhetsnavn>", saa begge varianter sjekkes.
  TOKEN=""
  for candidate in "$UNIT_NAME" "systemd-$UNIT_NAME"; do
    TOKEN="$(podman exec "$candidate" node -e 'console.log(JSON.parse(require("fs").readFileSync("/opt/nimay-omp-bridge/config.json","utf8")).token)' 2>/dev/null || true)"
    [[ -n "$TOKEN" ]] && break
  done
  echo "    $TOKEN"
  echo "==============================================================="
  exit 0
fi

bash "$HERE/scripts/configure.sh" "$@"
