# OMP-HANDOVER.md

# Nimay OMP Chat - Utviklingsoverlevering til OMP

Dette dokumentet gir OMP alt som trengs for å ta over videreutviklingen av Nimay OMP Chat.

---

## A. Prosjektoversikt

**Nimay OMP Chat** er en Android-chatklient som kobler til OMP (Oh My Pi)-agenten som kjører lokalt på en Windows-PC. Appen kommuniserer med en lokal OMP Bridge (Node.js) på port 7777 via LAN, Wi-Fi eller Tailscale.

Systemet består av fire hovedkomponenter:

| Komponent | Beskrivelse | Posisjon |
|-----------|-------------|----------|
| **OMP-agent** | Den autonome KI-agenten som kjører lokalt på PC-en | Windows (brukers maskin) |
| **OMP Bridge** | Node.js-bro som formidler mellom Android-app og OMP-agent | Windows (port 7777) |
| **Android-app (OmpChat)** | Chatklient med ChatGPT-inspirert UI | Android (APK) |
| **Nettside (chat.nimay.net)** | Nedlastingsside med APK, installer, update.json | Debian-server |

---

## B. Arkitektur

### Android-app (App-modul)

Appen er bygget med Android Studio, Kotlin og Jetpack Compose. Den bruker Gradle produktesmaker for å levere to varianter:

| Variant | Pakkenavn | APK-navn | Formål |
|---------|-----------|----------|--------|
| `classic` | `com.omp.chat` | `app-release.apk` | Eksisterende, stabil app (uendret) |
| `chstgpt` | `com.omp.chat.chstgpt` | `nimayomp-chatgpt-inspired.apk` | ChatGPT-inspirert UI (ny) |

**Bygg:** `.\gradlew.bat :app:assembleChstgptRelease` (Windows) eller `./gradlew assembleChstgptRelease` (Linux)

### OMP-kommunikasjon

Appen kommuniserer med OMP Bridge via HTTP/REST:

| Endepunkt | Beskrivelse |
|-----------|-------------|
| `POST /api/sessions` | Hente liste over lagrede sesjoner |
| `POST /api/session/messages` | Hente meldinger for en sesjon |
| `POST /api/sessions/open` | Åpne en lagret sesjon |
| `POST /api/send` | Sende melding til OMP (med streaming) |
| `POST /api/state` | Hente nåværende arbeidstilstand (streaming eller ikke) |
| `POST /api/active` | Hente aktiv bridge-sesjon |
| `POST /api/answer` | Svare på OMP-godkjenning (select, confirm, input, editor, ask) |
| `POST /api/models` | Hente tilgjengelige modeller |
| `GET /api/ping` | Sjekke at bridge er tilgjengelig |
| `GET /api/health` | Autentisert helsesjekk med token |

Paring skjer via QR-kode (`omp2://ip:port?token=...`) eller manuell adresseinntasting. Token lagres i `bridge/config.json` på PC-en.

### Streaming

OMP-agenten strømmer svar i realtid via `message_delta`-hendelser fra bridge. Android-appen mottar deltaløyter via SSE/HTTP og oppdaterer UI via `refreshLiveStatus()` i `ChatRepository.kt`. Arbeidstilstand bestemmes av:

1. `/api/state` fra bridge (authoritative for bridge-drevne sesjoner)
2. Transkript-heuristikk for terminal-drevne sesjoner (meldinger vokser, siste melding er bruker)

### Autentisering og sikkerhet

- Hver installasjon har en unik token lagret i `bridge/config.json`
- Alle API-kall bortsett fra `/api/ping` krever Bearer-token i Authorization-header
- Token genereres automatisk ved installasjon og lagres lokalt
- Ingen hardkodede passord eller IPs

---

## C. Bygging

### Forutsättninger (Windows)

- Android Studio / Android SDK (API 34)
- Gradle 8.x
- JDK 17
- Node.js (for bridge)
- Bun (for OMP-agent)

### Byggekommandoer

| Mål | Kommando (Windows) | Kommando (Linux) |
|-----|-------------------|------------------|
| Bygg classic APK | `.\gradlew.bat :app:assembleRelease` | `./gradlew :app:assembleRelease` |
| Bygg chstgpt APK | `.\gradlew.bat :app:assembleChstgptRelease` | `./gradlew :app:assembleChstgptRelease` |
| Bygg begge | `.\gradlew.bat :app:assembleRelease` | `./gradlew :app:assembleRelease` |
| Rens | `.\gradlew.bat clean` | `./gradlew clean` |

### APK-utdata

| Variant | Sti | Versjon |
|---------|-----|---------|
| classic | `app/build/outputs/apk/release/app-release.apk` | 2.10.1 (code 99) |
| chstgpt | `app/build/outputs/apk/chstgpt/release/nimayomp-chatgpt-inspired.apk` | 3.0.3-chatgpt-inspired (code 103) |

### Signering

Begge varianter signeres med eksisterende sertifikat:
- `CN=OmpChat, O=OmpChat, C=NO`
- Sertifikat SHA-256: `f1a3f7dbc626acbbbb107f970b8b2c092dc2f088cfc2d23a74089c109aef6200`
- Keystore: `keystore.properties` (må konfigureres lokalt)

### Oppgradering

APK kan installeres over tidligere versjon (samme pakkenavn `com.omp.chat.chstgpt`). Brukerdata (paring, token, samtalehistorikk) bevares.

---

## D. Utviklingsregler

### Generelle regler

1. **Hver leverte APK må få høyere versjonsnummer.** Aldri bruk samme versionCode for to forskjellige APK-filer.
2. **Classic-varianten må forblir uendret.** Endringer i `app/src/main/` påvirker begge varianter - test alltid begge.
3. **ChatGPT-inspired varianten bruker eget designsystem.** Ingen Nimay-blå farger i chstgpt-varianten.
4. **Bro- og Android-versjoner må holdes kompatible.** Endringer i bridge-API må koordineres med Android-app.
5. **Eksisterende paring og brukerdata må bevares.** Ikke endre token-lagring eller session-format uten migreringsplan.
6. **Ingen endring kalles verifisert uten testbevis.** Bruk PASS/FAIL/IKKE TESTET i alle rapporter.
7. **Kilkode må commites i Git.** Aldri jobb uten å committe regelmessig.
8. **Fungerende utgivelser må kunne gjenopprettes.** Merk alle release-commits.

### Versjonering

Format: `MAJOR.MINOR.PATCH-variant` (f.eks. `3.0.3-chatgpt-inspired`)
versionCode: Økes med 1 for hver release (103 for 3.0.3-chatgpt-inspired)

### ChatGPT-inspired designprinsipper

- Nå svart bakgrunn (#0D0D0D), ikke mørkeblått
- Inter-font (OFL lisens)
- Eget "N"-merke, ikke ChatGPT-logo
- Smart autoscroll: følger kun når brukeren er nederst i samtalen
- Ingen simulerte funktioner; all data kommer fra ekte bridge

---

## E. Kjente feil og begrensninger

| Område | Beskrivelse | Status |
|--------|-------------|--------|
| Fysisk Android-enhet | Ingen fysisk enhet tilgjengelig for testing; kun emulator | IKKE TESTET |
| Kamera-vedlegg | Kun galleri- og fil-vedlegg testet; kamera ikke testet | IKKE TESTET |
| Tale / STT / TTS | `FeatureFlags.VoiceEnabled = false`; tale-funksjonalitet deaktivert | IKTE TESTET |
| Wake-on-LAN (WOL) | WOL-felt finnes i Innstillinger, men ikke testet | IKKE TESTET |
| Lys-tema for legacy-skjermer | Klassiske skjermer (Innstillinger, Filer, osv.) bruker Nimay-paletten i chstgpt-varianten | IKKE TESTET |
| TUI-drevne sesjoner | Bridge hardkoder `streaming: false` for terminal-sesjoner; arbeidstilstand avledes fra transkriptvækst (heuristikk) | Dokumentert begrensning |
| Faktisk Windows-omstart | Ingen omstart utført i testmiljøet | IKKE TESTET |
| Oppgradering 3.0.2 → 3.0.3 | Testet som reinstallasjon på emulator, ikke som reell oppgradering | DELTESTET |

---

## F. Status og videre utvikling

### Fullført (versjon 3.0.3-chatgpt-inspired)

- ✅ Gradle produktesmaker: `classic` og `chstgpt`
- ✅ ChatGPT-inspirert mørkt design for alle chstgpt-skjermer
- ✅ Fjernet alle hardkodede Nimay-farger fra chstgpt-varianten (via VariantColors-system)
- ✅ Smart autoscroll med followsBottom-tilstand
- ✅ Dyplenk-paring (omp/omp2/ompchat)
- ✅ Ekte bridge-kommunikasjon (QR, token, sesjoner)
- ✅ Sending av meldinger med streaming
- ✅ Arbeidsindikator + Stopp-knapp
- ✅ Sesjonshistorikk, modellvelger, PC-bytt
- ✅ Arkiv, omdøp, slett sesjoner
- ✅ Innstillinger, Oppgaver, Terminal, Filbehandler
- ✅ APK signert med eksisterende sertifikat
- ✅ Publisert på chat.nimay.net

### Prioriterte videreutviklinger

1. **Fysisk enhetstesting** - Test på ekte Android-enhet (Motorola moto g86 power 5G eller lignende)
2. **Streaming-optimalisering** - Måle og optimaliser renderingsytelse for lange svar
3. **Tale / STT / TTS** - Aktiver `FeatureFlags.VoiceEnabled` og integrer talefunksjonalitet
4. **Lys-tema for alle skjermer** - Full lysmodus-støtte for chstgpt-varianten
5. **WOL-integrering** - Test og integrer Wake-on-LAN

---

## G. Bygging på Linux (Debian-server)

Android SDK må installeres manuelt på Debian-serveren for å kunne bygge APK:

```bash
# Installer Java 17
sudo apt update
sudo apt install openjdk-17-jdk

# Last ned Android SDK command-line tools
cd ~
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
mkdir -p ~/Android/cmdline-tools
unzip commandlinetools-linux-*.zip -d ~/Android/cmdline-tools
mv ~/Android/cmdline-tools/cmdline-tools ~/Android/cmdline-tools/latest

# Sett miljøvariabler
export ANDROID_HOME=~/Android
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools

# Installer nødvendige SDK-komponenter
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# Bygg APK
cd /projects/nimayomp
./gradlew assembleChstgptRelease
```

---

## H. Git-arbeidsflyt

Prosjektet er tilgjengelig som Git-repositorium på serveren:

```
git clone megaxenon@192.168.50.222:/projects/nimayomp
```

Eller fra samme maskin:

```
git clone /projects/nimayomp
```

### Arbeidsflyt

1. Klone repositoriet
2. Opprett en ny branch for hver funksjon/feilretting: `git checkout -b feature/min-feature`
3. Gjør endringer og test grunnlig
4. Commit med beskrivende melding: `git commit -m "feat: beskrivelse av endring"`
5. Push til server: `git push origin <branch-name>`
6. Oppdater versjonsnummer i `app/build.gradle` før hver release
7. Bygg APK og test på emulator/enhet før publisering

### Versjonsnummer-regler

- `versionCode` må økes med minst 1 for hver release
- `versionName` må følge formatet `X.Y.Z-variant`
- Aldri bruk samme versionCode for to forskjellige APK-filer
- Oppdater `PROJECT_STATUS.md` og `VERIFICATION-*.md` ved hver release

---

## I. Viktige filer og kataloger

| Fil/Katalog | Beskrivelse |
|-------------|-------------|
| `app/build.gradle` | Gradle-konfigurasjon, produktesmaker, versjonsnummer |
| `app/src/chstgpt/` | ChatGPT-inspirert variant-kode (UI, tema, aktivitet) |
| `app/src/main/` | Delt kode (klassisk + chstgpt) |
| `app/src/main/java/com/omp/chat/ui/theme/VariantColors.kt` | Variant-aware fargesystem |
| `app/src/main/java/com/omp/chat/ui/theme/Theme.kt` | Nimay-farger (classic variant) |
| `app/src/chstgpt/java/com/omp/chat/ui/chstgpt/ChatGptTheme.kt` | ChatGPT-farger (chstgpt variant) |
| `bridge/omp-bridge.mjs` | OMP Bridge-kode (Node.js) |
| `installer/Nimay-OMP-Bridge-Fresh.iss` | Inno Setup-installerskript (Windows) |
| `PROJECT_STATUS.md` | Prosjektstatus (oppdatér ved hver endring) |
| `VERIFICATION-CHSTGPT.md` | Testrapport (oppdatér ved hver release) |

---

## J. Kontakt og støtte

- E-post: chat@nimay.net
- Nettside: https://chat.nimay.net
- Nedlasting: https://chat.nimay.net/downloads/nimayomp/

---

*Dette dokumentet oppdateres ved hver større endring i prosjektet. Sist oppdatert: 2026-11-05.*
