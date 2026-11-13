# Verifisering – Nimay OMP Bridge Setup 3.3.5

Dato: 2026-11-05

## Endelig fil

- Installasjonsdefinisjon: `installer/Nimay-OMP-Bridge-Fresh.iss`
- EXE: `installer/dist/Nimay-OMP-Bridge-Setup.exe`
- Filversjon: `3.3.5`
- Størrelse: `2 150 391` byte
- SHA-256: `EF6EC3531990FD1219733E1BDBC5F2549BACE815239CBA652237387B78AC0AEF`
- Kodesignatur: `NotSigned`

## Faktiske testresultater

| Test | Resultat | Bevis |
|---|---|---|
| Kompilering med Inno Setup | PASS | ISCC exit 0; filversjon 3.3.5 |
| Ren installasjon fra ferdig EXE | PASS | `/VERYSILENT`, Setup exit 0, ca. 2,5 s |
| Installasjon isolert fra aktiv Bridge | PASS | Egen mappe under `%TEMP%\opencode`; aktiv installasjon ikke slettet eller erstattet |
| Standardport og standard-host | PASS | Separat fersk EXE-installasjon svarte autentisert på `0.0.0.0:7777`; Setup exit 0 |
| Alternativ isolert port | PASS | `17777` brukt slik at aktiv og isolert Bridge kunne testes uten filkonflikt |
| OMP-discovery | PASS | `OMP_BIN` fjernet fra testmiljøet; validert `bun.exe|...\dist\cli.js`, begge filer finnes |
| Autentisert health | PASS | `/api/health` med installasjonens token, Bridge 2.9.16 |
| Persistent token ved restart | PASS | Samme 48-tegns token etter stopp og start via Startup-snarvei |
| Én Bridge-instans | PASS | Startup-snarvei kjørt to ganger; samme PID og kun én listener |
| Én oppstartsmetode | PASS | Én Startup-snarvei; legacy Run-nøkkel fjernes av installeren |
| Oppgradering med eldre Run-nøkkel | PASS | Kunstig eldre Run-nøkkel ble fjernet; Startup-snarveien gjensto |
| `/ompserver` i ny OMP-sesjon | PASS | Kommando registrert og kjørt; startet isolert Bridge på 17777 |
| `/ompqr` i ny OMP-sesjon | PASS | Kommando registrert og kjørt; QR-bilde opprettet |
| QR-format og innhold | PASS | OpenCV dekodet `omp2://...:17777`; port og token matchet config |
| RPC og modellsvar | PASS | Ny sesjon opprettet, prompt sendt, assistent svarte `4` på `2+2` |
| Oppgradering/tokenbevaring | PASS | Token, `approvalMode`, `allowBash`, `projectRoots`, `idleMinutes`, `maxChildren` og voice-innstilling bevart |
| Ingen voice-/STT-/TTS-payload | PASS | Ingen Piper-, Whisper-, voice-, TTS- eller STT-installasjonsfiler i payload |
| Ingen maskinspesifikke kildestier | PASS | 0 treff på utviklerstier, brukernavn og tidligere hardkodede IP-eksempler i payload |
| Aktiv installasjon bevart | PASS | Samme token og config-hash etter testene; aktiv Bridge svarer på 7777 |
| Aktiv brukerkontekst | PASS | Bridge-prosessen eies av samme innloggede Windows-bruker |
| Opprydding | PASS | Ingen testprosesser/listener på 17777; kommando- og discovery-pekere gjenopprettet |

## Ikke testet

- **Fysisk Android QR-skanning:** IKKE TESTET. `adb devices` viste ingen enheter. QR-en er dekodet og validert på PC, men dette er ikke det samme som paring med telefon.
- **Windows-omstart/ny innlogging:** IKKE TESTET. Den faktiske Startup-snarveien ble startet manuelt to ganger.
- **Installasjon på en annen fysisk Windows-PC:** IKKE TESTET. Payload er kontrollert for maskinspesifikke stier/IP-er.
- **Interaktiv veiviser:** IKKE TESTET. Ren installasjon og oppgradering ble kjørt silent. Definisjonen har ingen Voice Bridge-/stemmemodellsider.

## GitHub-verifisering

- Release: https://github.com/megaxenon83-server/OmpChat/releases/tag/v3.3.5
- Direkte EXE: https://github.com/megaxenon83-server/OmpChat/releases/download/v3.3.5/Nimay-OMP-Bridge-Setup.exe
- GitHub API: asset `uploaded`, størrelse `2 150 391`, SHA-256-digest som over.
- EXE lastet ned fra release og hashet på nytt: identisk SHA-256.
- 13 eldre installer-assets fjernet etter verifisering av 3.3.4; det midlertidige 3.3.4-assetet ble fjernet etter verifisering av 3.3.5.
- GitHub-kontroll etter opprydding: kun `v3.3.5/Nimay-OMP-Bridge-Setup.exe` gjenstår som offisiell installer.
- `OmpChat-2.9.74.apk` er uendret: SHA-256 `27967D8DA9D73D54CD404070C3E4D72FC207BE061BBA29BED54FD7017876DB0C`.

## Bygg

Payload bygges fra `bridge/omp-bridge.mjs`, `omp-commands/` og kun disse verktøyene:

- `start-bridge-hidden.ps1`
- `stop-bridge.ps1`
- `omp_qr.py`
- `qrgen.mjs`

Deretter:

```powershell
& 'C:\Users\Megaxenon\AppData\Local\Programs\Inno Setup 6\ISCC.exe' /Qp 'installer\Nimay-OMP-Bridge-Fresh.iss'
```

3.3.1, 3.3.2, 3.3.3 og 3.3.4 er ikke lenger offisielle installer-assets. 3.3.5 er den eneste offisielle Windows-installeren.
