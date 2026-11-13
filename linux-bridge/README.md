# Nimay OMP Bridge — Linux-pakkinger

## Innhold

| Fil | Beskrivelse |
|-----|-------------|
| `nimay-omp-bridge_1.0.0_amd64.deb` | Debian/Ubuntu/Mint/Pop!_OS pakke |
| `nimay-omp-bridge-1.0.0-1.noarch.rpm` | Fedora/RHEL/CentOS/AlmaLinux/Rocky |
| `nimay-omp-bridge.container` | Quadlet-fil for Bazzite/Silverblue/uBlue |
| `Containerfile` | Bygger Docker/Podman-image |
| `install.sh` / `uninstall.sh` | Standalone-installer (fallback) |

---

## Hurtigstart

### 1. Vanlig Linux (Debian/Ubuntu/Fedora/RHEL)
```bash
# Debian/Ubuntu/Mint/Pop!_OS
sudo dpkg -i nimay-omp-bridge_1.0.0_amd64.deb
# eller
sudo apt install ./nimay-omp-bridge_1.0.0_amd64.deb

# Fedora/RHEL/CentOS/AlmaLinux/Rocky
sudo rpm -Uvh nimay-omp-bridge-1.0.0-1.noarch.rpm
# eller
sudo dnf install ./nimay-omp-bridge-1.0.0-1.noarch.rpm
```

**Det er alt.** Tjenesten starter, aktiveres ved boot, og kjører på port 7802.

---

## Bazzite / Fedora Silverblue / uBlue / uCore (immutable)

### Alternativ 1: rpm-ostree (anbefalt for standardbruk)
```bash
rpm-ostree install ./nimay-omp-bridge-1.0.0-1.noarch.rpm
systemctl reboot
```
- Installeres som layer i ostree → overlever `rpm-ostree upgrade`
- systemd-tjeneste starter automatisk ved boot
- Ingen innlogging nødvendig for at tjenesten kjører

### Alternativ 2: Quadlet (Podman + systemd) — *mest Bazzite-native*
```bash
# Som root:
sudo cp nimay-omp-bridge.container /etc/containers/systemd/
systemctl daemon-reload
systemctl enable --now nimay-omp-bridge
```
- Kjører i container (Podman) med systemd-styring (Quadlet)
- Ingen rot-skrift til filsystemet (immutable-friendly)
- Oppdateres via `podman auto-update` / `systemctl reload nimay-omp-bridge`
- Kjører som din bruker (tilgang til `~/.omp`, `omp` i PATH)

---

## Konfigurasjon

Etter installasjon genereres en token automatisk. Se token med:
```bash
cat /opt/nimay-omp-bridge/token
# eller
systemctl status nimay-omp-bridge
```

### Paring med Android-appen
1. Åpne Nimay OMP-appen på Android
2. Trykk "Legg til PC" → skann QR-kode eller skriv inn:
   ```
   omp2://<PC-IP>:7802?token=<TOKEN>
   ```
   Eller bruk QR-koden som vises i appen ved pairing.

---

## Port og nettverk
- Standard port: **7802** (TCP)
- Bindes til `0.0.0.0` (alle interfaces)
- Fungerer over LAN, Tailscale, WireGuard, Tailscale Funnel

---

## Oppgradering
```bash
# Debian/Ubuntu
sudo dpkg -i nimay-omp-bridge_<ny>.deb
# eller
sudo apt install ./nimay-omp-bridge_<ny>.deb

# Fedora/RHEL
rpm -Uvh nimay-omp-bridge-<ny>.rpm
# eller
sudo dnf install ./nimay-omp-bridge-<ny>.rpm

# Bazzite (rpm-ostree)
rpm-ostree install ./nimay-omp-bridge-<ny>.rpm
systemctl reboot
```
Bridge-token og konfigurasjon bevares ved oppgradering.

---

## Fjerning
```bash
# Debian/Ubuntu
sudo apt remove nimay-omp-bridge

# Fedora/RHEL
sudo rpm -e nimay-omp-bridge
```

---

## Filoversikt

```
linux-bridge/
├── nimay-omp-bridge_1.0.0_amd64.deb          # Debian/Ubuntu-pakke
├── nimay-omp-bridge-1.0.0-1.noarch.rpm       # Fedora/RHEL-pakke
├── nimay-omp-bridge.container                # Quadlet-fil (Bazzite/Silverblue)
├── Containerfile                             # Docker/Podman-image
├── install.sh                                # Standalone-installer (fallback)
├── uninstall.sh
└── payload/
    ├── bridge/
    │   ├── omp-bridge.mjs           # Bridge-kjørbar (ESM)
    │   ├── config.template.json     # Konfigurasjonsmal
    │   ├── scripts/configure.sh     # Installasjons-/konfig-skript
    └── nimay-omp-bridge.service     # systemd-unit
├── tools/                           # Verktøy (QR, APK-server, osv.)
└── voice/
    └── voice_runtime.py             # Python voice runtime (valgfritt)
```

---

## Bygge fra kilde (utviklere)

```bash
# Bygg RPM
cd packaging/rpm
rpmbuild -ba SPECS/nimay-omp-bridge.spec

# Bygg DEB
cd ../deb
dpkg-deb --build deb-root nimay-omp-bridge_1.0.0_amd64.deb

# Bygg container-image
podman build -t nimay-omp-bridge:latest -f Containerfile .
```

---

## Lisens
MIT — se `LICENSE` i hovedrepo.

---

**Utviklet av Nimay OMP** — https://chat.nimay.net/nimayopp/