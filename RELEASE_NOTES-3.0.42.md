# Nimay OMP v3.0.42 / Bridge 2.9.25

Kildekode og bygde pakker for Nimay OMP — Android-appen, Windows-bridgeen og Linux-installasjonen.

## Hva er nytt

### Android-app 3.0.42 (versionCode 142)
- Oppdateringssjekkeren leser nå `version.json` fra den offentlige nedlastingsadressen
- Nedlasting og installasjon av APK direkte i appen (FileProvider)
- Innstillinger siden er omskrevet
- `versionName` bumpet fra 3.0.34 til 3.0.42

### Bridge 2.9.25 (Windows)
- **Retter ENOENT-feilen:** broen slår opp OMP-binæren i PATH i stedet for en hardkodet bun-shim-sti som ikke finnes lenger. Uten denne feilen feilet *alle* forespørsler.
- `config.template.json` inneholder nå `__TOKEN__`-plassholder. Installasjonen genererer et ekte token ved første kjøring og beholder det ved reinstallasjon — så paringen til mobilappen ikke brytes.
- Installasjonsskriptet kopierer mappeinnhold flatt, slik at gjentatte installasjoner ikke lager nestede `tools\tools`-mapper.
- `omp-bin.txt` skrives ved installasjon, slik at broen finner `omp.exe` uten å være avhengig av PATH eller `OMP_BIN`.
- Ferdigkontrollen bruker `/api/health` med token. Den gamle `/health`-ruten finnes ikke, så kontrollen rapporterte alltid «Bridge svarer ikke».

### Linux-installer 1.0.0 (.deb, .rpm, Quadlet-container)
- Pakker samme bridge som 2.9.25
- **Retter ENOENT-feilen på Linux:** `write-omp-bin.sh` fester OMP-binæren. Uten den falt broen tilbake på `~/.bun/bin/omp.exe` — en Windows-sti som aldri finnes på Linux.
- `tools/node_modules` følger med, så `/ompqr` virker
- Alle pakker bruker LF-linjeslutt. CRLF brøt `dpkg-deb` («blank line in value of field 'Description'») og `rpmbuild` («%prep: : not found»)
- Containerfile og Quadlet-unit er rettet for det nye oppsettet
- `install.sh --container` setter opp Quadlet-varianten for Bazzite/Silverblue/uBlue/uCore

### Release-verktøy (`release/`)
- `New-Release.ps1` — oppretter en ny versjonert release-mappe per patch
- `Bump-And-Release.ps1` — øker patch-nivået og publiserer
- `Install-All.ps1` — installerer Windows og Linux
- `Build-LinuxPackages.ps1` — bygger `.deb` og `.rpm`
- `Restart-Bridge.ps1` — starter broen på nytt uten tap av tilgang (staging på parallellport, verifisering, overtagelse, tilbakerulling ved feil)
- `Update-Index.ps1` — bygger `index.json` fra mappene på disk

---

## Filer i denne releasen

| Fil | Plattform | Beskrivelse |
|---|---|---|
| `nimayomp-chatgpt-inspired-3.0.42.apk` | Android | Android-appen, chatgpt-inspired-varianten |
| `nimay-omp-bridge_1.0.0_amd64.deb` | Debian/Ubuntu/Mint/Pop!_OS | Bridge + systemd-tjeneste |
| `nimay-omp-bridge-1.0.0-1.noarch.rpm` | Fedora/RHEL/CentOS/AlmaLinux | Bridge + systemd-tjeneste |
| `nimayomp-source-3.0.42.zip` | Alle | Full kildekode |

---

## Installasjon

### Windows
```powershell
# Fra kildearkivet: bridge-installer\
powershell -ExecutionPolicy Bypass -File .\bridge-installer\Install-OMPBridge.ps1 -Port 7778
```

### Linux (Debian/Ubuntu)
```bash
sudo dpkg -i nimay-omp-bridge_1.0.0_amd64.deb
```

### Linux (Fedora/RHEL)
```bash
sudo rpm -Uvh nimay-omp-bridge-1.0.0-1.noarch.rpm
```

### Linux (Bazzite/Silverblue/uBlue/uCore)
```bash
podman build -t nimay-omp-bridge:latest -f Containerfile linux-bridge/
sudo cp linux-bridge/nimay-omp-bridge.container /etc/containers/systemd/
sudo sed -i 's/<BRUKER>/din_brukernavn/g' /etc/containers/systemd/nimay-omp-bridge.container
sudo systemctl daemon-reload && sudo systemctl enable --now nimay-omp-bridge
```

### Kilder fra scratch
```bash
git clone https://github.com/megaxenon83-server/OmpChat.git
cd OmpChat
# Android:  ./gradlew assembleChstgptRelease
# Bridge:   ./release/Install-All.ps1
```

---

## Krav

| Komponent | Krav |
|---|---|
| Android | Android 8.0+, SDK 31+ |
| Bridge (Windows) | Node.js 20+, OMP (`omp.exe`) i PATH |
| Bridge (Linux) | Node.js 18+, `omp` installert med `npm install -g --prefix ~/.local @oh-my-pi/pi-coding-agent` samt `bun` |
| Bridge (container) | Podman, Fedora Minimal-baseret image |

---

## Lisens

Se `LICENSE` i kildearkivet.
