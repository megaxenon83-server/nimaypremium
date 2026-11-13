# Verifikasjonsrapport – Nimay OMP Bridge Installer 3.3.6 (auto-start-rettelse)

**Dato:** 2026-11-05  
**Installer:** `installer\dist\Nimay-OMP-Bridge-Setup.exe`  
**Versjon:** 3.3.6  
**SHA-256:** `C608AA40D7805455674877E6550EC37BAB2B6FF37414F9738464FED0F99BE706`  
**Størrelse:** 2 150 838 byte  
**Testmiljø:** Windows 11, brukerkonto `Megaxenon`, Node.js + Bun + OMP installert.

---

## Rotårsak (bekreftet på testmaskinen)

Installer 3.3.5 (og tidligere versjoner) hadde tre samtidige problemer som gjorde at Bridge fungerte rett etter installasjon, men ikke etter Windows-omstart:

| # | Problem | Konsekvens etter omstart |
|---|---------|--------------------------|
| 1 | Oppstartsgenvei `Nimay OMP Bridge.lnk` i Startup-mappen pekte til en midlertidig test-mappe (`...\Temp\opencode\test-final\tools\start-bridge-hidden.ps1`) i stedet for installasjonsmappen (`%LOCALAPPDATA%\Nimay\OMPBridge\tools\start-bridge-hidden.ps1`) | Windows prøvde å starte Broen fra en feil/mest sannsynlig ikke-eksisterende bane → Bro startet ikke |
| 2 | `omp-bin.txt` manglet i den installer bridge-mappen | Fallback-lesing av `OMP_BIN` i `start-bridge-hidden.ps1` fant ingenting |
| 3 | Eldre `start-bridge-hidden.ps1` var installert (ikke build-fresh versjonen) | Skriptet les ikke `OMP_BIN` fra `omp-bin.txt` eller HKCU-registret, og brukte ikke token-basert `/api/health` |

**Hvorfor reinstallasjon midlertidig reparerte problemet:** Installers `CurStepChanged(ssPostInstall)` starter Broen direkte med riktig miljø (OMP_BIN er tilgjengelig i installasjonsprosessen). Broen fungerer derfor umiddelbart etter installasjon. Men oppstartsgenveien (som peker feil) og/eller det gamle skriptet fungerer ikke etter Windows-omstart, når miljøet er nullstilt og genveien må bære hele lasten.

---

## Rettelser i versjon 3.3.6

### `Nimay-OMP-Bridge-Fresh.iss` (installerskript)

1. **Versjonbump:** 3.3.5 → 3.3.6
2. **`RemoveOldStartupShortcuts()` i `CurStepChanged(ssInstall)`:** Fjerner alle `Nimay OMP Bridge*-genveier` fra brukerens Startup-mappe før [Icons] oppretter den korrekte genveien på ny. Dette fjerner både `.disabled`-kopier og genveier som peker til feile steder fra tidligere bygg.
3. **`omp-bin.txt` skrives alltid:** Under `ssPostInstall` lagres det detektede `OMP_BIN` til `{app}\bridge\omp-bin.txt`. `start-bridge-hidden.ps1` leser denne som fallback hvis `OMP_BIN`-miljøvariabelen ikke er tilgjengelig i oppstartsprosessen.
4. **Kun per-user autostart:** `{userstartop}` (brukerens Startup-mappe), ingen SYSTEM-tjeneste, ingen `C:\Program Files`, ingen hardkodede brukernavn eller PC-spesifikke stier, `PrivilegesRequired=lowest`.

### `start-bridge-hidden.ps1` (build-fresh versjon, installeres med 3.3.6)

- Leser `OMP_BIN` i rekkefølge: miljøvariabel → `omp-bin.txt` → HKCU Environment-registret
- Bruker token-basert `/api/health`-sjekk (unngår å forveksle med en annen Bro på samme port)
- Omdirigerer stdout/stderr til `bridge.stdout.log` / `bridge.stderr.log` for feilsøking
- Venter opp til 20 sekunder på at Bro svarer

---

## Tester utført

Alle tester utført på testmaskinen etter stille installasjon av 3.3.6 over den eksisterende installasjonen.

| # | Test | Resultat | Bevis |
|---|------|----------|-------|
| 1 | Stille installasjon (`/SILENT /NORESTART`) | PASS | Install exit 0 |
| 2 | Oppstartsgenvei peker til korrekt installasjonsmappe | PASS | Target: `powershell.exe`, Args: `-File "C:\Users\Megaxenon\AppData\Local\Nimay\OMPBridge\tools\start-bridge-hidden.ps1"`, WorkDir: `%LOCALAPPDATA%\Nimay\OMPBridge` |
| 3 | Ingen gjenværende `.disabled`-genvei | PASS | Kun `Nimay OMP Bridge.lnk` finnes i Startup-mappen |
| 4 | `omp-bin.txt` opprettet i bridge-mappe | PASS | Inneholder detektet `OMP_BIN` (bun.exe\|cli.js) |
| 5 | Installert `start-bridge-hidden.ps1` er build-fresh versjon | PASS | Leser `omp-bin.txt` (linje 15), HKCU `OMP_BIN` (linje 23), token-basert `/api/health` (linje 59) |
| 6 | Oppstartsskript oppdager kjørende Bro | PASS | Exit 0, melding "Broen svarer allerede på port 7777" |
| 7 | `/api/health` med Bearer-token | PASS | `ok=True, version=2.9.16, omp=<bun.exe path>` |
| 8 | `/api/ping` | PASS | `version=2.9.16` |
| 9 | `/api/state` | PASS | OK |
| 10 | `OMP_BIN` i HKCU Environment | PASS | `bun.exe\|.../cli.js` (tilgjengelig for nye prosesser etter pålogging) |

---

## IKKE TESTET

Disse tester kunne ikke utføres i dette testmiljøet og er markert som **IKKE TESTET** i henhold til leveranseskravene:

| # | Test | Årsak |
|---|------|-------|
| A | Faktisk Windows-omstart | Ingen omstartstillatelse i dette miljøet |
| B | Bro starter automatisk etter omstart | Avhenger av test A |
| C | `/ompserver` og `/ompqr` fungerer etter omstart | Avhenger av test A |
| D | Android kan koble til Bro etter omstart | Avhenger av test A |
| E | Oppgradering fra 3.3.5 → 3.3.6 (reell oppgraderings scenario) | Testet som stille reinstallasjon, ikke som oppgradering over en eldre installert versjon |

---

## Konklusjon

Rotårsaken til at Bridge fungerte etter installasjon men ikke etter Windows-omstart er identifisert og rettet i installer 3.3.6. De tre samtidige problemene (feil oppstartsgenvei, manglende `omp-bin.txt`, eldre startskript) er alle adressert.

Installer 3.3.6 er bygget, signert (med eksisterende build-verktøy, ikke kodesignert) og verifisert på testmaskinen. Alle tester som kunne utføres uten faktisk Windows-omstart er **PASS**.

**Viktig:** Faktisk Windows-omstartstest (test A–D) er **IKKE TESTET** og må utføres på målmaskinen før installer 3.3.6 kan erklæres fullstendig verifisert for produksjonsbruk.
