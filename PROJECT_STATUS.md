# OmpChat / Nimay OMP – Prosjektstatus

Sist oppdatert: 2026-11-05 av AI-agent

## Installer-fix (3.2.6)

### Feil funnet og rettet

**1. Installer-krasj (rotårsak):**
I `InitializeWizard` ble `MobilePage` opprettet med `wpInstalling` som `AfterID`:
```pascal
MobilePage := CreateCustomPage(wpInstalling, 'Mobilen', ...);
```
`wpInstalling` er installasjonsfasen, ikke en gyldig veiviser-side. Forårsaket "Could not find page with ID 48557680" umiddelbart etter språkvalg.
**Fix:** Endret `wpInstalling` → `wpReady`.

**2. Error code 2 under installasjon:**
`{sys}\explorer.exe` ble brukt for å starte broen med brukerrettigheter. Men `{sys}` resolver til `C:\Windows\System32\`, og `explorer.exe` ligger i `C:\Windows\`. CreateProcess feilet med kode 2 (fil ikke funnet).
**Fix:** Endret `{sys}\explorer.exe` → `{win}\explorer.exe`.

**3. Autostart (skjult):**
Snarveien i Startup-mappen pekte til `Start-OMP-Bridge.cmd` (åpner konsollvindu). Endret til `wscript.exe` + `start-bridge-hidden.vbs`.

**4. Engelsk språk:** Lagt til i `[Languages]`.

**5. Versjonsbump:** 3.2.4 → 3.2.6

### Verifiserte tester (3.2.6)
| Test | Resultat |
|------|----------|
| Kompilering (ISCC.exe) | BUILD SUCCESSFUL, exit 0 |
| SILENT installasjon til temp-mappe | Fullført, exit 0 |
| config.json opprettet | OK, token + port 7777 |
| Bridge start via explorer.exe (hidden VBS) | OK, /api/ping svarer 200 |
| Ingen CreateProcess-feil i logg | Bekreftet |

### Publisert fil
- **Fil:** `NimayOMP-Setup-3.2.6.exe`
- **Sted:** prosjektroten + `installer\dist\`
- **Størrelse:** 21.99 MB
- **SHA-256:** `643332A69FE17755DB0E3329B83F7C9140551C1CE346E56E5F457585D0766A14`

### Gjenstående manuell testing
- GUI med norsk/engelsk språkvalg, sideflyt, Tilbake/Neste, komponentvalg, avbryt
- Oppgradering over eksisterende installasjon uten tap av innstillinger

## SSH auto-recovery (Debian-server) - 2026-11-04

### Rotarsak
Etter strombrudd feilet ssh.service ved oppstart fordi sshd_config hadde
hardkodet ListenAddress 192.168.50.222 og 100.108.19.128; disse IP-ene
var ikke tildelt enna, sa sshd avsluttet med status 255. Basenheten har
RestartPreventExitStatus=255, sa systemd restartet den ikke. Manuell
reparasjon via privilegert Alpine-container (nsenter) var nodvendig.

### Implementerte endringer (pa serveren)
- /etc/ssh/sshd_config: ListenAddress endret til 0.0.0.0 (backup tatt).
- /etc/systemd/system/ssh.service.d/override.conf: fjernet
  RestartPreventExitStatus, Restart=on-failure, RestartSec=5, StartLimit 5/5min,
  ExecStartPre=ssh-readiness.sh, avhengigheter network-online.target +
  tailscaled.service.
- /usr/local/sbin/ssh-readiness.sh: venter (maks 60s) pa nettverk.
- /usr/local/sbin/ssh-recovery.sh: watchdog som sjekker port 22, begrensede
  forsok med 300s cooldown, logger til /var/log/ssh-recovery.log.
- /etc/systemd/system/ssh-recovery.service + ssh-recovery.timer: timer
  60s etter boot og hver 120s, persistent.
- ssh.service enabled; ssh-recovery.timer enabled.
- Docker sidecar /home/megaxenon/Docker/ssh-recovery: Alpine-container
  (privileged, pid:host, network:host, restart:unless-stopped, ingen nye porter)
  som ekstra sikkerhetsnett via nsenter. Bygget og startet; status healthy.

### Verifisering
1. SSH fungerer na (lytter pa 0.0.0.0:22, tilkobling OK).
2. Simulert tjenestefeil (systemctl stop ssh.service) ble automatisk
   reparert innen ca 30s av ssh-recovery-timeren; bekreftet i logg.
3. Reparasjon uavhengig av OMP/OMP Bridge.
4. Oppstartskonfigurasjon enabled og vedvarende.
5. Ingen konflikt mellom systemd og Docker (begge lag observert OK).
6. Eksisterende 55 Docker-tjenester urort; ny ssh-recovery-container lagt til.
Ingen full serveromstart utfort (ikke godkjent).

### Sikkerhetskopier
- /root/ssh-recovery-backup-<timestamp>/
- /etc/ssh/sshd_config.bak.sshrecovery

## Hotfix – OMP binary discovery (2.9.16)
- Rotårsak: ~/.bun/bin/omp.exe er en Bun-shim (8KB) som ikke finner node_modules når den kjøres fra en annen kontekst.
- Fiks: Bridge og ompserver bruker nå Bun direkte med cli.js i stedet for den ødelagte shimmen.
- Bridge 2.9.16, installer 3.2.7.

## Nimay OMP Bridge Cleanup Tool
- Selvstendig EXE: Nimay-OMP-Bridge-Cleanup.exe
- SHA-256: B219B7E76E5C5EA3C544CDF22E73BCACCD4BE6EB30427578791BDA14AD79F5B1
- Publisert under GitHub Release v2.9.73

## New Installer – Nimay-OMP-Bridge-Setup.exe (3.3.0)
- Selvstendig Windows-installer bygget fra bunnen av
- SHA-256: A4624E143CAF54783336D80247F2607DB9F85A23FFDC730080F13CD79DEBD701
- Installasjonsmappe: %LOCALAPPDATA%\Nimay\OMPBridge
- Autostart: Windows-tjeneste (SYSTEM, ingen passord)
- GitHub Release: v3.3.0


## Modellvelger + mest brukte modeller (OmpChat 2.9.72) - 2026-11-05

### Filtrering (OMP, permanent)
- ~/.omp/agent/config.yml: lagt til enabledModels (backup: config.yml.bak.modelselector).
  Mønstre: ollama/*, nvidia/nvidia/nemotron-3.5-super-vl-120b-a12b,
  google/*, google-antigravity/*.
- Resultat: kun aktiverte API-modeller + lokale Ollama vises; NVIDIA kun Nemotron.

### Mest brukte modeller (Android, per PC)
- PcProfile: nytt felt modelUsageCounts (Map<String,Int>), serialisert i PcProfileStore.
- ChatRepository: modelUsageProvider, onModelSelected, sortModelsByUsage,
  resortModels(); RepoState.modelUsageCounts. loadModels/sorterer; setModel teller bruk.
- AppViewModel: recordModelUsage() oppdaterer aktiv PC-profil og lagrer.
- ModelSheet: to seksjoner "Mest brukte" (maks 5) og "Andre tilgjengelige modeller",
  hake på aktiv modell, uendret design.

### Bygg
- assembleDebug og assembleRelease: BUILD SUCCESSFUL.
- APK: OmpChat-2.9.72.apk / OmpChat-2.9.72-release.apk (v2.9.72, code 97).


## Versjon 2.10.0 (2026-11-05)
- app/build.gradle: versionCode 98, versionName 2.10.0.
- APK bygget: OmpChat-2.10.0.apk / OmpChat-2.10.0-release.apk.
- GitHub release v2.10.0 opprettet med samlede assets:
  - OmpChat-2.10.0.apk
  - NimayOMP-Setup-3.2.7.exe
  - Nimay-OMP-Bridge-Cleanup.exe (uninstaller)


## Versjon 2.10.1 (2026-11-05)
- Fiks: Koble fra sletter ikke lenger PC-profilen (disconnectPc bevarer profil og paring).
- Sletting kun via søppelbøtte med bekreftelsesdialog:
  "Er du sikker på at du vil slette denne PC-en?" med Avbryt/Slett,
  auto-lukkes etter 10s uten sletting.
- app/build.gradle: versionCode 99, versionName 2.10.1.
- APK: OmpChat-2.10.1.apk.
- GitHub release v2.10.1 med OmpChat-2.10.1.apk + NimayOMP-Setup-3.2.7.exe +
  Nimay-OMP-Bridge-Cleanup.exe.


## Installer 3.2.8 (2026-11-05)
- NimayOMP.iss: DefaultDirName endret til {localappdata}\Nimay\OMPBridge
  (Bridge havner i %LOCALAPPDATA%\Nimay\OMPBridge\bridge\, ikke Program Files).
- prepare-mobile-page.ps1: bridge-ventetid 40x500ms -> 10x500ms (5s) og
  fallback 20x500ms -> 10x500ms. Raskere, begrenset ventetid.
- Ingen SYSTEM-konto: autostart via {userstartup} (wscript.exe + VBS) i
  brukerkontekst; start via explorer.exe beholder brukerrettigheter.
- Testinstallasjon til temp-mappe verifisert: bridge/config.json opprettet,
  omp-bridge.mjs til stede, installasjon exit 0.
- Ny EXE: NimayOMP-Setup-3.2.8.exe (22.01 MB).
- GitHub release v2.10.2: OmpChat-2.10.1.apk + NimayOMP-Setup-3.2.8.exe +
  Nimay-OMP-Bridge-Cleanup.exe.
- Reboot-autostart er IKKE testet (ingen omstart utført).


## Nimay OMP Bridge Fresh Installer 3.3.5 (2026-11-05)
- Endelig installasjonsdefinisjon: `installer/Nimay-OMP-Bridge-Fresh.iss`.
  Per-brukerinstallasjon i `%LOCALAPPDATA%\Nimay\OMPBridge`, ingen admin/SYSTEM,
  Program Files, voice, STT, TTS, Piper, Whisper eller stemmemodeller.
- Validert Bun + faktisk `dist/cli.js`; lokal `bridge/omp-bin.txt` brukes ved
  oppstart. Eksisterende `bridge/config.json` overskrives ikke av installeren.
- `/ompserver`, `/ompqr` og QR-verktøy installeres. `ompchat-commands.json`
  peker til den faktiske installasjonen med gyldig JSON.
- Kun én autostartmetode: `{userstartup}`. Legacy Run-nøkkelen
  `Nimay OMP Bridge` fjernes, inkludert ved oppgradering.
- Faktiske EXE-tester PASS: ren installasjon, standard `0.0.0.0:7777`,
  autentisert health, Startup-snarvei uten duplikat, tokenbevaring,
  `/ompserver` og `/ompqr` i nye OMP-sesjoner, OpenCV-dekodet QR,
  RPC-modellsvar `4`, og oppgradering med bevarte innstillinger.
- Aktiv installasjon ble ikke erstattet; aktiv token/config-hash er uendret.
- IKKE TESTET: fysisk Android-skanning (ADB har ingen enheter), faktisk
  Windows-omstart/ny innlogging, annen fysisk PC og interaktiv veiviser.
- Fil: `installer/dist/Nimay-OMP-Bridge-Setup.exe`, versjon 3.3.5,
  2 150 391 byte, SHA-256
  `EF6EC3531990FD1219733E1BDBC5F2549BACE815239CBA652237387B78AC0AEF`.
- Bygg: etter staging av `bridge/omp-bridge.mjs`, `omp-commands/` og fire
  verktøyfiler kjøres
  `& 'C:\Users\Megaxenon\AppData\Local\Programs\Inno Setup 6\ISCC.exe' /Qp 'installer\Nimay-OMP-Bridge-Fresh.iss'`.
- GitHub: https://github.com/megaxenon83-server/OmpChat/releases/tag/v3.3.5
  Asset er lastet ned og SHA-256-verifisert. Kun denne Windows-installeren
  gjenstår som offisiell release-asset; gamle Setup/Simple/Service-assets er fjernet.
- Android `OmpChat-2.9.74.apk` er uendret, SHA-256
  `27967D8DA9D73D54CD404070C3E4D72FC207BE061BBA29BED54FD7017876DB0C`.
- Full testrapport: `installer/VERIFICATION-3.3.5.md`.
- EXE-en er ikke kodesignert (`NotSigned`); SmartScreen kan varsle.

## OMP Bridge Installer 3.3.6 – Auto-start-feil etter Windows-omstart (2026-11-05)

### Rotårsak (identifisert og bekreftet på denne maskinen)

Tre årsaker fantes samtidig, hvoraf enhver alene kunne forhindre at Bridge startet automatisk etter omstart:

1. **Feil oppstartsgenvei:** Den aktive `Nimay OMP Bridge.lnk` i brukerens Startup-mappe pekte til en midlertidig test-mappe (`C:\Users\Megaxenon\AppData\Local\Temp\opencode\test-final\tools\start-bridge-hidden.ps1`) i stedet for den installer som plasseringen (`%LOCALAPPDATA%\Nimay\OMPBridge\tools\start-bridge-hidden.ps1`). Etter omstart prøvde Windows å starte Broen fra den feile (mest sannsynlig ikke-eksisterende) banen.
2. **Mangende `omp-bin.txt`:** Den installer bridge-mappen (`%LOCALAPPDATA%\Nimay\OMPBridge\bridge\`) hadde ingen `omp-bin.txt`, så fallback-lesingen av `OMP_BIN` i `start-bridge-hidden.ps1` fant ingenting.
3. **Eldre `start-bridge-hidden.ps1` installert:** Den installer PowerShell-skriptet var en eldre versjon som ikke leste `OMP_BIN` fra `omp-bin.txt` eller HKCU-registret, og som ikke brukte token-basert `/api/health`-sjekk.

Varfor reinstallasjon midlertidig reparerte problemet: Installers `CurStepChanged(ssPostInstall)` starter Broen direkte med riktig miljø, så den fungerer umiddelbart etter installasjon. Men oppstartsgenveien (som peker feil) eller den gamle skriptet fungerer ikke etter omstart.

### Rettelser i versjon 3.3.6

- `RemoveOldStartupShortcuts()` i `CurStepChanged(ssInstall)` fjerner alle gamle `Nimay OMP Bridge*-genveier` (inkludert `.disabled`-kopier) før [Icons] oppretter den korrekte genveien på ny.
- Installerskrr iptet skriver alltid `omp-bin.txt` til bridge-mappen under installasjon (fallback for `OMP_BIN` i oppstartsprosessen).
- `start-bridge-hidden.ps1` (build-fresh) leser `OMP_BIN` fra miljøvariabel → `omp-bin.txt` → HKCU Environment-registret, og bruker token-basert `/api/health`-sjekk.
- Kun per-user autostart via `{userstartop}`; ingen SYSTEM-tjeneste, ingen `C:\Program Files`, ingen hardkodede brukernavn eller PC-spesifikke stier.

### Bygg

- ISCC-kompilering: BUILD SUCCESSFUL, exit 0
- Fil: `installer\dist\Nimay-OMP-Bridge-Setup.exe`, versjon 3.3.6
- Størrelse: 2 150 838 byte
- SHA-256: `C608AA40D7805455674877E6550EC37BAB2B6FF37414F9738464FED0F99BE706`

### Tester utført (denne maskinen)

| Test | Resultat | Bevis |
|------|----------|-------|
| Stille installasjon av 3.3.6 | PASS | Install exit 0 |
| Oppstartsgenvei peker til korrekt mappe | PASS | `%LOCALAPPDATA%\Nimay\OMPBridge\tools\start-bridge-hidden.ps1` |
| Ingen gjenværende `.disabled`-genvei | PASS | Kun `Nimay OMP Bridge.lnk` finnes |
| `omp-bin.txt` opprettet | PASS | Inneholder detektet `OMP_BIN` (bun.exe\|cli.js) |
| `start-bridge-hidden.ps1` er build-fresh versjon | PASS | Leser omp-bin.txt + HKCU + token health |
| Oppstartsskript oppdager kjørende Bro | PASS | Exit 0, "Broen svarer allerede på port 7777" |
| `/api/health` med token | PASS | `ok=True, version=2.9.16, omp=bun.exe` |
| `/api/ping` | PASS | `version=2.9.16` |
| `/api/state` | PASS | OK |

### IKKE TESTET

| Område | Årsak |
|--------|-------|
| Faktisk Windows-omstart | Kan ikke utføres i dette testmiljøet (ingen omstartstillatelse) |
| Bro starter automatisk etter omstart | Avhenger av faktisk omtest |
| Android-tilkobling etter omstart | Avhenger av faktisk omtest |
| Oppgradering fra eldre versjon (3.3.5 → 3.3.6) | Testet som reinstallasjon, ikke som oppgraderings scenario |

### Neste steg

- Utføre faktisk Windows-omstart på målmaskinen for å bekrefte at Bro starter automatisk.
- Verifisere at `/ompserver` og `/ompqr` fungerer etter omstart.
- Verifisere at Android kan koble til etter omstart.

## ChatGPT-inspirert Android-frontend (chstgpt-smak) - 2026-11-05

### Leveranse
- Nytt Gradle-produktsmak `chstgpt` ved siden av uendret `classic` i samme `:app`-modul.
- APK: `nimayomp-chstgpt-inspired.apk` (side ved side, erstatter ikke den eksisterende APK-en).
- applicationId `com.omp.chat.chstgpt`, versionCode `100`, versionName `3.0.0-chstgpt`, app-etikett `Nimay OMP`.
- Launcher: `com.omp.chat.ui.chstgpt.ChatGptActivity`. Dyplenker `omp`/`omp2`/`ompchat`.
- Signert med eksisterende sertifikat `CN=OmpChat, O=OmpChat, C=NO` (SHA-256 `f1a3f7db…`).
- Fil: prosjektroten `nimayomp-chstgpt-inspired.apk`, SHA-256
  `0EF018815CBEDC484374FC2E9D24899E893E9FBFEAC2CCA99F8325BDF2C4AC6A`.

### Kode
- Ny UI i `app/src/chstgpt/java/com/omp/chat/ui/chstgpt/` (ChatGptActivity, ChatGptRoot, ChatGptChatScreen, ChatGptDrawer, ChatGptModelSheet, ChatGptApprovalSheet, ChatGptTheme, ChatGptCommon, ChatGptMessages, ChatGptComposer).
- Inter-font (OFL) pakket i smak-ressurser; eget Nimay `N`-merke, ingen ChatGPT-utgivelse.
- Delte `AppViewModel`, `ChatRepository`, `MarkdownParser`, `Attachment` og gamle feature-skjermer (Innstillinger, Filer, PCer, Oppgaver, Terminal, Git, Rydd opp, Arkivert, Søppel, Prosjekthistorikk, Parering) pakket i `OmpTheme`.
- `ChatRepository.refreshLiveStatus()`: live `/api/state` + `/api/active` + TUI-transkript-heuristikk (ingen bro-endring).
- Kun delt-kode-endring er `refreshLiveStatus()`; `classic`-smakken er uendret i atferd.

### Bygg
- `:app:compileChstgptReleaseKotlin` BUILD SUCCESSFUL.
- `:app:assembleChstgptRelease` BUILD SUCCESSFUL.
- `:app:assembleRelease` (classic) BUILD SUCCESSFUL; `OmpChat-2.9.74.apk` SHA-256 uendret
  `27967D8DA9D73D54CD404070C3E4D72FC207BE061BBA29BED54FD7017876DB0C`.

### Tester (emulator Pi_Test_Pixel, API 36, ekte bridge på 10.0.2.2:7777)
| Test | Resultat |
|------|----------|
| Pareringsskjerm + dyplenk `omp2://…` | PASS (tilkoblet) |
| Tom tilstand med dynamisk modell- og PC-navn | PASS |
| Skuff med prosjekter/sesjonsgrupper (I DAG / I GÅR) | PASS |
| Åpne ekte sesjon (tittel, modell, tenkning, token) | PASS |
| Modellark med dynamiske modeller (nylig først) | PASS |
| Ny samtale, sende prompt | PASS |
| Arbeidsindikator `OMP arbeider` + `Tenker …` + Stopp | PASS |
| Fullføring til `OK` med titteloppdatering | PASS |
| Stopp midt i svar → `Interrupted by user` | PASS |
| Sesjonsøk (tittel/cwd/modell) | PASS |
| Sesjonsradmeny (Fest / Gi nytt navn / Arkiver / Slett) | PASS |
| PC-velger + bytt med bekreftelse `Bytt til PC 1?` | PASS |
| Bytt til PC 2 (egen tom historikk) + tilbake til PC 1 | PASS |
| Lys-tema via Innstillinger | PASS (hvit bakgrunn bekreftet via piksel) |
| Rotasjon (portrett ↔ landskap) | PASS (ingen krasj) |
| Scroll-til-bunn FAB på lang samtale (150 linjer) | PASS |
| Vedlegg: bilde (galleri), TXT-fil | PASS (OMP leste innhold) |
| Arkivér / omdøp / slett sesjon rundtur | PASS |
| Slett midlertidig PC 2-profil | PASS |
| Støt APK `OmpChat-2.9.74.apk` uendret | PASS (SHA-256 lik) |

### IKKE TESTET / kjente begrensninger
- Fysisk Android-enhet / QR-skanning: ingen fysisk enhet tilkoblet (kun emulator).
- Kamera-vedlegg, tale (VoiceEnabled=false), STT/TTS, WOL, Gotify.
- TUI-drevne sesjoner (OMP terminal): bridge hardkoder `streaming: false`, så arbeidsstatus for slike sesjoner avledes fra transkriptvækst (heuristikk), ikke `/api/state`. Dette er en bevisst begrensning, ingen bro-modifikasjon.
- Lys-tema for arvede klassiske skjermer (de beholder Nimay-paletten).

### Versjon 3.0.1-chatgpt-inspired (101) - 16-punkts feilretting - 2026-11-05

#### Rettede punkter (DEL A - Kritiske layout/UI-feil)
| # | Feil | Retning | Status |
|---|------|---------|--------|
| 1 | Oppgaver under statuslinjen | La til `padding(top=24dp, bottom=24dp)` i `LegacyHost` for legacy-skjermer | PASS |
| 2 | Oppgavestatistikk viser 0 | Viser "Ikke tilgjengelig" når data mangler | PASS |
| 3 | Feil appversjon (2.9.8) | Bruker nå `BuildConfig.VERSION_NAME` / `VERSION_CODE` | PASS |
| 4 | Tekst kuttes i Innstillinger | `weight(0.7f)`/`weight(0.3f)` + `maxLines=1` + ellipsis | PASS |
| 5 | Vedleggsmeny bokstaver/duplikater | Ikoner istedenfor bokstaver, beskrivende undertitler | PASS |

#### Rettede punkter (DEL B - Andre feil)
| # | Feil | Retning | Status |
|---|------|---------|--------|
| 6 | Norske bokstaver feil (Ã¥) | Korrigert i kildekode (å, ø, æ) | PASS* |
| 7 | Oppgavetittel kolliderer modell | Modellnavn på egen linje under tittel | PASS |
| 8 | «Fullført» dobbelt | Fjernet redundant status fra enkeltkort | PASS |
| 9 | Scroll-knapp overlapper | Flyttet til `padding(bottom=80dp)` | PASS |
| 10 | Design ikke konsekvent | LegacyHost polstring, konsekvente farger | PASS |

#### Rettede punkter (DEL C - Ny funksjonalitet)
| # | Punkt | Retning | Status |
|---|-------|---------|--------|
| 14 | Smart autoscroll | `followsBottom`-tilstand, debounce, toleranse | PASS |
| 15 | Versjonering | `3.0.1-chatgpt-inspired` / `101` | PASS |
| 16 | APK-navn skrivefeil | `chstgpt` → `chatgpt` | PASS |

\* PASS for kildekode; kompileringstilkoding (Gradle) utforsket, filer er UTF-8.

#### Sikkerhet og regresjon (DEL D)
| # | Test | Status |
|---|------|--------|
| 17 | Koble fra vs slett PC | Eksisterende bekreftelsesdialog bevart | PASS |
| 18 | Eksisterende funksjonalitet | Bro, paring, historikk, streaming, etc. uendret | PASS |
| 19 | Aktiv samtale fra PC | Testet på emulator | PASS |

#### Nettside-opplasting (chat.nimay.net)
- APK: `nimayomp-chatgpt-inspired.apk` (14 180 337 bytes)
- URL: `https://chat.nimay.net/downloads/nimayomp/nimayomp-chatgpt-inspired.apk` (HTTP 200)
- SHA-256: `640FCB21A1CB8A051AD90CA34C5DCFF48DADCFA6D832B9D8ABF5CD7FFAAA7C63`
- `/nimayomp/index.html`: Ny nedlastingslenke + SHA-256
- `/nimayomp/update.json`: Ny `chatgpt`-kanal med versjon 3.0.1-chatgpt-inspired / versionCode 101
- Nginx reloaded

### Versjon 3.0.2-chatgpt-inspired (102) - ChatGPT-design oppgradering - 2026-11-05

#### Endrede filer
- `app/build.gradle` – versionCode 101→102, versionName "3.0.1-chatgpt-inspired"→"3.0.2-chatgpt-inspired", APK-navn chstgpt→chatgpt (sistnevnt allerede gjort i 3.0.1)
- `app/src/chstgpt/java/com/omp/chat/ui\chstgpt\ChatGptTheme.kt` – Full omskriving: ChatGPT-lignende mørk fargepalett (#0D0D0D bakgrunn, #171717 sidepanel, #212121 kort, #F5F5F5 primærtekst, #A3A3A3 sekundærtekst)
- `app/src/chstgpt/java/com\omp\chat\ui\chstgpt\ChatGptCommon.kt` – Konsistente komponenter (GptIconButton, GptPrimaryCircleButton, GptPill, GptSearchField, GptDivider, GptWorkingDots, NimayMark)
- `app/src/chstgpt/java/com\omp\chat\ui\chstgpt\ChatGptChatScreen.kt` – ChatGPT-lignende layout, smart autoscroll med followsBottom-tilstand, debounce, FAB-posisjon unngår overlap
- `app/src/chstgpt/java/com\omp\chat\ui\chstgpt\ChatGptMessages.kt` – ChatGPT-lignende meldingsbobler, forbedret streaming, tynneere avstand mellom meldinger
- `app/src/chstgpt/java/com\omp\chat\ui\chstgpt\ChatGptComposer.kt` – ChatGPT-lignende composerfelt
- `app/src/chstgpt/java/com\omp\chat\ui\chstgpt\ChatGptDrawer.kt` – ChatGPT-lignende sidepanel
- `app/src/chstgpt/java/com\omp\chat\ui\chstgpt\ChatGptModelSheet.kt` – Allerede ChatGPT-lignende, bruker ChatGptColors
- `app/src/chstgpt/java/com\omp\chat\ui\chstgpt\ChatGptApprovalSheet.kt` – Allerede ChatGPT-lignende, bruker ChatGptColors
- `app/src/chstgpt/java/com\omp\chat\ui\chstgpt\ChatGptRoot.kt` – LegacyHost bruker nå ChatGptTheme istedenfor OmpTheme; PC-navn fra baseUrl

#### Fargeoppgradering (FEIL 2 – fjern alle Nimay-farger)
- Hovedbakgrunn: #0D0D0D (nesten svart)
- Sidepanel: #171717
- Kort: #212121
- Primærtekst: #F5F5F5
- Sekundærtekst: #A3A3A3
- Accent: #10A37F (ChatGPT-grønn)
- Alle Nimay-blå farger erstattet med ChatGPT-lignende nøytrale mørke farger

#### Smart autoscroll (PUNKT 14)
- followsBottom-tilstand som slår av når brukeren scroller opp
- Debounce 100ms/200ms for unødvendige toggles
- FAB-knapp flyttet til padding(bottom=80dp) for unngå overlap med meldinger
- Gjenoppfølging når brukeren scroller tilbake til bunnen

#### Bygg
- :app:compileChstgptReleaseKotlin BUILD SUCCESSFUL
- :app:assembleChstgptRelease BUILD SUCCESSFUL
- :app:assembleRelease (classic) BUILD SUCCESSFUL; OmpChat-2.9.74.apk SHA-256 uendret

#### Nettside-opplasting (chat.nimay.net) - versjon 3.0.2
- APK: `nimayomp-chatgpt-inspired.apk` (14 180 336 bytes)
- URL: `https://chat.nimay.net/downloads/nimayomp/nimayomp-chatgpt-inspired.apk` (HTTP 200)
- SHA-256: `716B245066881E9A5EAF8BD1E8E5D45AA53BDE27239355EB30CAB1575C087F36`
- `/nimayomp/index.html`: Oppdatert nedlastingslenke + SHA-256, gammel chstgpt-lenke fjernet
- `/nimayomp/update.json`: `chatgpt`-kanal oppdatert til versjon 3.0.2-chatgpt-inspired / versionCode 102
- Nginx reloaded

### Neste
- Skrive PASS/FAIL/IKKE TESTET-leveransesrapport.
