# Installasjonsguide: Bazzite / Silverblue / uBlue / uCore (Quadlet)

## Hva er Quadlet?

Quadlet er systemd-integrasjon for Podman-containere. Det lar deg kjøre containere som systemd-tjenester uten rot-skrift til filsystemet – perfekt for immutable OS som Bazzite, Silverblue, uBlue, uCore, Fedora Silverblue.

---

## Forutsetninger

- Bazzite / Fedora Silverblue / uBlue / uCore
- Podman + systemd (standard på alle ovennevnte)
- Bruker med sudo-tilgang
- **OMP installert på verten.** Containeren har OMP innebygd, men brukeren din trenger også OMP lokalt hvis du vil bruke `omp` fra terminalen.

---

## Viktig: hva containeren trenger fra verten

Containeren kjører med UID 1000 (`omp`-brukeren i image-et) og trenger to ting fra verten:

| Hva | Hvorfor | Eierskap |
|---|---|---|
| `~/.omp` | OMP lagrer **autentiseringen** i `agent.db` og modellkonfigurasjonen i `models.yml`. Uten hele `~/.omp` får broen ingen modeller og svarer «No models available». | UID 1000 |
| `/opt/nimay-omp-bridge` | Konfigurasjon, token og logger. | UID 1000 |

**`~/.omp` må eies av UID 1000.** Ellers får broen `EACCES: permission denied, mkdir '/home/omp/.omp/agent'` og alle forespørsler feiler.

---

## Installasjon (som root)

```bash
# 1. Kopier Quadlet-fil til systemd
sudo cp nimay-omp-bridge.container /etc/containers/systemd/

# 2. Oppdater brukernavn i .container-filen (erstatt <BRUKER> med ditt brukernavn)
sudo sed -i 's/<BRUKER>/din_brukernavn/g' /etc/containers/systemd/nimay-omp-bridge.container

# 3. Gi ~/.omp riktig eierskap slik at containeren kan skrive til den
sudo mkdir -p "$HOME/.omp"
sudo chown -R 1000:1000 "$HOME/.omp"

# 4. Last systemd-konfigurasjon
sudo systemctl daemon-reload

# 5. Aktiver og start tjenesten
sudo systemctl enable --now nimay-omp-bridge
```

Eller bruk installasjonsskriptet, som gjør alt dette for deg:

```bash
sudo ./install.sh --container
```

### Image

`.container`-filen peker på det ferdigbygde image-et:

```
ghcr.io/megaxenon83-server/ompchat/omp-bridge:latest
```

Det hentes automatisk. For å bygge lokalt i stedet:

```bash
podman build -t nimay-omp-bridge:latest -f Containerfile .
sudo sed -i 's|ghcr.io/megaxenon83-server/ompchat/omp-bridge:latest|nimay-omp-bridge:latest|' \
  /etc/containers/systemd/nimay-omp-bridge.container
```

Trenger du OMP-modeller? Sørg for at `~/.omp/agent/models.yml` og `~/.omp/agent/agent.db` finnes, og at de er eid av UID 1000:

```bash
sudo chown -R 1000:1000 "$HOME/.omp"
```

---

## Verifiser installasjon

```bash
# Sjekk status
systemctl is-active nimay-omp-bridge
# → active

systemctl is-enabled nimay-omp-bridge
# → enabled

# Sjekk port
ss -tln | grep 7802
# → LISTEN 0.0.0.0:7802

# Hent token fra containeren
TOKEN=$(podman exec nimay-omp-bridge node -e 'console.log(JSON.parse(require("fs").readFileSync("/opt/nimay-omp-bridge/config.json","utf8")).token)')

# Test API
curl -H "Authorization: Bearer $TOKEN" http://127.0.0.1:7802/api/ping
# → {"ok":true,"name":"omp-bridge","version":"2.9.25","port":7802,...}
```

---

## Konfigurasjon

Token genereres automatisk ved første oppstart og ligger i `config.json` inne i containeren:

```bash
podman exec nimay-omp-bridge cat /opt/nimay-omp-bridge/config.json
```

Porten kan endres i `config.template.json` før bygging, eller ved å redigere `config.json` og restarte:

```bash
sudo systemctl restart nimay-omp-bridge
```

---

## Feilsøking

### «No models available» eller `OMP svarte ikke innen 60 sekunder`

Containeren finner ikke auth-konfigurasjonen. Sjekk at `~/.omp/agent/` inneholder `models.yml` og `agent.db`, og at mappen er eid av UID 1000:

```bash
sudo chown -R 1000:1000 "$HOME/.omp"
podman exec nimay-omp-bridge ls -la /home/omp/.omp/agent/
```

### `EACCES: permission denied, mkdir '/home/omp/.omp/agent'`

Samme årsak. `~/.omp` eies av feil bruker. Se over.

### `spawn .../omp.exe ENOENT`

`omp-bin.txt` peker på en Windows-sti. Bygg image-et på nytt, så blir den skrevet med riktig verdi:

```bash
podman build -t nimay-omp-bridge:latest -f Containerfile .
```

### `Error: unknown flag: --entry`

Bruker en gammel `omp-bridge.mjs`. Kopier den nyeste fra `bridge/omp-bridge.mjs`.

### Containeren dør med `EADDRINUSE` på port 7802

Den native tjenesten kjører allerede på porten. Stopp den:

```bash
sudo systemctl disable --now nimay-omp-bridge
```

---

## Oppdatering

```bash
cd linux-bridge
podman build -t nimay-omp-bridge:latest -f Containerfile .
sudo systemctl restart nimay-omp-bridge
```

Image-et er lokalt. For automatisk oppdatering fra et registry må image-et publiseres dit, og `Image=` i `.container`-filen pekes dit.

---

## Se også

- `README.md` – oversikt over alle Linux-variantene
- `INSTALL_DEBIAN.md` – Debian/Ubuntu/Mint/Pop!_OS
- `INSTALL_FEDORA.md` – Fedora/RHEL/CentOS/AlmaLinux/Rocky
