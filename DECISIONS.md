# OmpChat DECISIONS.md - Viktige tekniske beslutninger

Dette dokumentet inneholder viktige tekniske beslutninger som er tatt for OmpChat-prosjektet.

## Beslutninger

### v2.3 - APK-installasjonskrasj på Android 16 (2026-10-28)

**Beslutning:** Fjern adaptivt ikon og splash-ikon referanser for å unngå pakkeinstallatør-krasj på Android 16.

**Begrundelse:** Androids pakkeinstallatør krasjet med `width and height must be > 0` når den prøvde å lage en bitmap av app-ikonet. Adaptive-ikonet (`ic_launcher.xml` i `mipmap-anydpi-v26`) refererte til `@mipmap/ic_launcher_foreground` som ikke hadde gyldige dimensjoner på API 36.

**Endringer:**
- Slettet `mipmap-anydpi-v26/ic_launcher.xml` og `ic_launcher_round.xml`
- Slettet alle `ic_launcher_foreground.png` og `ic_launcher_round.png`
- Slettet `drawable-nodpi/splash_icon.png`
- Endret `values-v31/themes.xml` - fjernet `windowSplashScreenAnimatedIcon`
- Endret `AndroidManifest.xml` - `roundIcon` peker nå til `@mipmap/ic_launcher`

### v2.3 - QR-kode avkuttet i terminalwidget (2026-10-28)

**Beslutning:** Reduser QR-kodestørrelse og åpne PNG alltid i bildeviser.

**Begrundelse:** QR-koden ble for stor for terminalwidgeten i OMP-agenten på telefonen, noe som førte til `widget truncated`.

**Endringer:**
- `tools/omp_qr.py`: `ERROR_CORRECT_M` → `ERROR_CORRECT_L`, `border=4` → `border=2`
- `omp-commands/ompqr/index.ts`: `openPath(pngPath)` kalles alltid, ikke bare i headless-modus

### v2.1 - UI/UX redesign (2026-10-27)

**Beslutning:** Full UI/UX-overhaling inspirert av ChatGPT mobilapp.

**Begrundelse:** Appen opplevdes som for stor, klumpete og lite profesjonell.

**Endringer:**
- Ny fargepalett (mørk modus: #101314 bakgrunn, #151A1C sidepanel, etc.)
- Ny typografi (15 sp body, 17-18 sp overskrifter)
- Ny chat-layout (brukermeldinger høyre, AI-svar uten boble, kompakt avstand)
- Ny sidepanel med søk, prosjektvelger, gruppert samtaleliste
- Ny meldingsfelt med auto-høyde, plussikon, mikrofon, send/stopp

## Arkiverte beslutninger

Ingen arkiverte beslutninger ennå.
