# OmpChat — globale OMP-kommandoer

To egendefinerte slash-kommandoer for Oh My Pi (OMP), tilgjengelige fra alle prosjektmapper:

| Kommando | Gjør |
|---|---|
| `/ompserver` | Starter `bridge/omp-bridge.mjs` som en løsrevet bakgrunnsprosess, eller viser status hvis den allerede kjører. |
| `/ompqr` | Lager parings-QR (Tailscale-IP først) med `tools/omp_qr.py` og viser den i OMP-terminalen. |

## Hvordan OMP laster dem

OMP 18.12.6 laster TypeScript-kommandoer fra:

```
%USERPROFILE%\.omp\agent\commands\<navn>\index.ts
```

Katalogen er bruker-global, så kommandoene virker i alle prosjekter. Modulen må
eksportere en fabrikk som default og returnere `{ name, description, execute }`.
Dette er OMPs egen mekanisme — ikke OpenCode-kommandoer og ikke plugin-markdown.

## Installasjon

```powershell
powershell -ExecutionPolicy Bypass -File tools\install_omp_commands.ps1
```

Scriptet:

- kopierer `omp-commands\ompchat-shared`, `ompserver` og `ompqr` til
  `%USERPROFILE%\.omp\agent\commands\`
- skriver `%USERPROFILE%\.omp\agent\ompchat-commands.json` (prosjektrot, UTF-8 uten BOM)
- rører ikke `bridge/config.json` eller tokenet

Start OMP på nytt etter installasjon, fordi kommandoer lastes ved oppstart.

## `/ompserver`

- Sjekker `http://127.0.0.1:<port>/api/ping` først; starter aldri duplikater.
- Leser port fra `bridge/config.json` (BOM-tolerant) og bruker filen uendret.
- Starter `node omp-bridge.mjs` med `detached: true` og logg i
  `%TEMP%\opencode\bridge.out.log` / `.err.log`; prosessen overlever at OMP lukkes.
- Viser status, PID, port, lokal IP, Tailscale-IP, godkjenningsmodus og loggsti
  som en widget over editoren.
- `/ompserver status` viser bare status uten å starte.
- Tømmer loggfilene ved oppstart, slik at eldre tokens ikke blir liggende i loggen.

## `/ompqr`

- Krever at broen svarer; ellers vises «Kjør /ompserver først».
- Foretrekker Tailscale-IPv4 (100.64.0.0/10), ellers privat LAN-IPv4.
- Kjører `python tools/omp_qr.py --bridge <host>:<port> --terminal --out omp_chat_qr.png`.
  `--terminal` skriver kompakt halvblokk-QR og **ingen** token/payload-tekst.
- Viser QR-en som widget. I headless/RPC-modus åpnes PNG-en i stedet.
- `/ompqr clear` skjuler widgeten, `/ompqr open` åpner PNG-en.
- `/ompqr download` — start lokal filserver for APK-nedlasting fra PC.
- All fanget output maskeres (`token=***`) før den vises.

## Krav

- Node.js 20+ i `PATH` (bridgen).
- OMP på `%USERPROFILE%\.bun\bin\omp.exe` eller `OMP_BIN`.
- Python 3 med `qrcode` og `pillow` for `/ompqr` (`python -m pip install qrcode pillow`).

## Verifisert

- Begge kommandoene dukker opp i OMPs ekte kommandokatalog
  (`get_available_commands` over RPC): 54 kommandoer, inkl. `/ompserver` og `/ompqr`.
- `/ompserver` via OMP RPC: startet broen, `agentInvoked: false` (ingen LLM-kall),
  broen svarte fortsatt etter at OMP-prosessen ble avsluttet.
- Gjentatt `/ompserver` med broen oppe: samme PID, ingen duplikat.
- `/ompqr` via OMP RPC: PNG generert; avkodet med zxing-cpp til nøyaktig
  `omp2://100.96.46.66:7777?token=…` (token ikke skrevet ut).
- Widget-innholdet verifisert: 29 linjer, Tailscale først, QR + PNG-sti, uten token.
- Feilvei: broen nede → «Kjør /ompserver først».
