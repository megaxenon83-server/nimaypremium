# Installasjonsguide: Debian / Ubuntu / Mint / Pop!_OS

## Hurtiginstallasjon (anbefalt)

```bash
# Last ned og installer i ett steg
sudo dpkg -i nimay-omp-bridge_1.0.1_amd64.deb
# eller
sudo apt install ./nimay-omp-bridge_1.0.1_amd64.deb
```

Dette:
1. Installerer bridge til `/opt/nimay-omp-bridge`
2. Genererer unik token i `/opt/nimay-omp-bridge/token`
3. Oppretter og aktiverer systemd-tjeneste `nimay-omp-bridge`
4. Starter tjenesten umiddelbart
5. Aktiverer autostart ved boot (`systemctl enable`)

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
# → {"ok":true,"name":"omp-bridge","version":"2.9.24",...}
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
sudo dpkg -i nimay-omp-bridge_<ny_versjon>.deb
# eller
sudo apt install ./nimay-omp-bridge_<ny>.deb
```
Token og konfigurasjon bevares ved oppgradering.

---

## Fjerning
```bash
sudo apt remove nimay-omp-bridge
```

---

## Feilsøking

| Problem | Løsning |
|---------|---------|
| Tjeneste starter ikke | `journalctl -u nimay-omp-bridge -f` |
| Port 7802 allerede i bruk | `ss -tlnp \| grep 7802` → stopp prosessen |
| Ingen token i config | `cat /opt/nimay-omp-bridge/token` |
| Tjeneste starter ikke ved boot | `systemctl enable --now nimay-omp-bridge` |

---

## Fjerning
```bash
sudo apt remove nimay-omp-bridge
# Fjerner alt unntatt brukerdata i ~/.omp/
```

---

## Avansert: Kjør som annen bruker
Standard installasjon kjører som `root`. For å kjøre som din bruker:
```bash
# Installer manuelt med install.sh
sudo ./install.sh --user $USER --port 7802
```
Dette setter opp systemd-tjeneste som kjører som din bruker (med tilgang til `~/.omp` og `omp` i PATH).