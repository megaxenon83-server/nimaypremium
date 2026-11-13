# Installasjonsguide: Fedora / RHEL / CentOS / AlmaLinux / Rocky Linux

## Hurtiginstallasjon (anbefalt)

```bash
# Fedora / RHEL 9+ / AlmaLinux / Rocky Linux
sudo dnf install ./nimay-omp-bridge-1.0.1-1.noarch.rpm
# eller
sudo rpm -Uvh --nodeps nimay-omp-bridge-1.0.1-1.noarch.rpm
```

> Bruk `dnf install ./...rpm` (anbefalt): den loeser avhengigheter som
> `nodejs >= 18` automatisk. `rpm -Uvh` alone feiler hvis Node.js ikke
> er installert fra for.

Dette:
1. Installerer bridge til `/opt/nimay-omp-bridge`
2. Genererer unik token i `/opt/nimay-omp-bridge/token`
3. Oppretter og aktiverer systemd-tjeneste `nimay-omp-bridge`
4. Starter tjenesten umiddelbart
5. Aktiverer autostart ved boot (`systemctl enable`)

---

## Bazzite / Fedora Silverblue / uBlue / uCore (immutable)

```bash
# Installer via rpm-ostree (layer i ostree)
rpm-ostree install ./nimay-omp-bridge-1.0.1-1.noarch.rpm
systemctl reboot
```
- Installeres som layer i ostree → overlever `rpm-ostree upgrade`
- systemd-tjeneste starter automatisk ved boot
- Ingen innlogging nødvendig for at tjenesten kjører

---

## Verifiser installasjon

```bash
# Sjekk at tjenesten kjører
systemctl is-active nimay-omp-bridge
# → active

# Sjekk at autostart er aktivert
systemctl is-enabled nimay-omp-bridge
# → enabled

# Sjekk at port 7802 lytter
ss -tln | grep 7802
# → LISTEN 0.0.0.0:7802

# Test API med token
TOKEN=$(cat /opt/nimay-omp-bridge/token)
curl -H "Authorization: Bearer $TOKEN" http://127.0.0.1:7802/api/ping
# → {"ok":true,"name":"omp-bridge","version":"2.9.24","port":7802,...}
```

---

## Bazzite / Silverblue / uBlue / uCore (Quadlet - alternativ)

For "native" Bazzite-opplevelse uten rpm-ostree:

```bash
# Som root:
sudo cp nimay-omp-bridge.container /etc/containers/systemd/
systemctl daemon-reload
systemctl enable --now nimay-omp-bridge
```

**Fordeler:**
- Kjører i container (Podman) med systemd-styring (Quadlet)
- Ingen rot-skrift til filsystemet (immutable-friendly)
- Oppdateres via `podman auto-update` / `systemctl reload nimay-omp-bridge`
- Kjører som din bruker (tilgang til `~/.omp`, `omp` i PATH)

---

## Verifiser installasjon

```bash
# Sjekk at tjenesten kjører
systemctl is-active nimay-omp-bridge
# → active

# Sjekk at autostart er aktivert
systemctl is-enabled nimay-omp-bridge
# → enabled

# Sjekk at port 7802 lytter
ss -tln | grep 7802
# → LISTEN 0.0.0.0:7802

# Test API med token
TOKEN=$(cat /opt/nimay-omp-bridge/token)
curl -H "Authorization: Bearer $TOKEN" http://127.0.0.1:7802/api/ping
# → {"ok":true,"name":"omp-bridge","version":"2.9.24","port":7802,...}
```

---

## Bruk med Android-appen

1. Finn token:
   ```bash
   cat /opt/nimay-omp-bridge/token
   ```

2. I Nimay OMP Android-appen:
   - Trykk **"Legg til PC"**
   - Skriv inn: `omp2://<PC-IP>:7802?token=<DIN_TOKEN>`
   - Eller skann QR-kode fra appen

---

## Oppgradering

```bash
# Fedora/RHEL
rpm -Uvh nimay-omp-bridge-<ny>.rpm
# eller
sudo dnf install ./nimay-omp-bridge-<ny>.rpm

# Bazzite (rpm-ostree)
rpm-ostree install ./nimay-omp-bridge-<ny>.rpm
systemctl reboot
```

Token og konfigurasjon bevares ved oppgradering.

---

## Fjerning
```bash
# Standard RPM
sudo rpm -e nimay-omp-bridge

# Eller via dnf
sudo dnf remove nimay-omp-bridge
```

---

## Fjerning (Quadlet)
```bash
systemctl stop nimay-omp-bridge
systemctl disable nimay-omp-bridge
sudo rm /etc/containers/systemd/nimay-omp-bridge.container
systemctl daemon-reload
```

---

## Feilsøking

| Problem | Løsning |
|---------|---------|
| `rpm -Uvh` sier "nodejs >= 18 is needed" | Bruk `sudo dnf install ./nimay-omp-bridge-1.0.1-1.noarch.rpm` i stedet - den loeser avhengigheten automatisk. |
| Tjeneste starter ikke | `journalctl -u nimay-omp-bridge -f` |
| Port 7802 allerede i bruk | `ss -tlnp | grep 7802` → stopp prosessen |
| Ingen token i config | `cat /opt/nimay-omp-bridge/token` |
| Tjeneste starter ikke ved boot | `systemctl enable --now nimay-omp-bridge` |
| Broen svarer, men kan ikke starte omp | Installer omp: `npm install -g --prefix ~/.local @oh-my-pi/pi-coding-agent` og bun: `curl -fsSL https://bun.sh/install | bash`, deretter kjør `sudo /opt/nimay-omp-bridge/scripts/configure.sh` på nytt. |
| rpm-ostree: "package already installed" | `rpm-ostree uninstall nimay-omp-bridge` først |

---

## Avansert: Kjør som annen bruker
Standard installasjon kjører som system-tjeneste. For å kjøre som din bruker (tilgang til `~/.omp`, `omp` i PATH):
```bash
# Kjør configure.sh manuelt med din bruker
sudo /opt/nimay-omp-bridge/scripts/configure.sh --user $USER --port 7802
```
Dette setter opp systemd-tjeneste som kjører som din bruker (med tilgang til `~/.omp` og `omp` i PATH).