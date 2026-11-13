# Nimay OMP – Release-struktur

Hver patch får sin **egen mappe** med versjonsnavn. Ingenting overskrives,
og du slipper å bygge i den samme mappen hele tiden.

```
C:\Prosjekter\Nimayomp\releases\
├── index.json                                  <- oversikt over alle releases
├── bridge\
│   └── nimay-omp-bridge-2.9.25\                <- omp-bridge.mjs, config.template.json, tools\, commands\
├── android\
│   ├── nimayomp-chatgpt-inspired-3.0.42\      <- nimayomp-chatgpt-inspired-3.0.42.apk
│   └── nimayomp-chatgpt-inspired-3.0.41\
└── linux\
    └── nimay-omp-bridge-1.0.0\                 <- .deb, .rpm, container, install.sh, *.md
```

Hver release-mappe inneholder også en `release.json` med versjon, tidsstempel
og SHA-256 for hver artefakt.

---

## Bruk

### Ny patch (anbefalt)

Bump versjonen i kilden **og** publiser i én operasjon:

```powershell
cd C:\Prosjekter\Nimayomp\release

.\Bump-And-Release.ps1 -Component bridge
.\Bump-And-Release.ps1 -Component android -BuildApk
.\Bump-And-Release.ps1 -Component linux  -BuildLinuxPackages
```

`Bump-And-Release.ps1` øker patch-nivået (2.9.25 → 2.9.26), skriver den nye
versjonen tilbake til kildefilen, og oppretter release-mappen.

### Publiser uten å bumpe

```powershell
.\New-Release.ps1 -Component bridge -Version 2.9.26 -Notes "Fikset fs/walk"
```

### Se hva som vil skje (ingen filer skrives)

```powershell
.\New-Release.ps1 -Component android -WhatIfOnly
.\Bump-And-Release.ps1 -Component linux -WhatIfOnly
```

### Oppdater oversikten

```powershell
.\Update-Index.ps1
```

`index.json` bygges fra mappene på disk, så den er alltid i sync.

---

## Versjonsregler

Følger `AGENTS.md` i Android-kilden:

- **Bridge** og **Android-app** versjoneres **uavhengig** av hverandre
- Ny patch må ha **høyere** versjonsnummer
- Gamle utgivelser **beholdes** i release-arkivet
- Filnavnet skal alltid inneholde versjonsnummeret
  (`nimayomp-chatgpt-inspired-3.0.42.apk`)

Mappenavnet bruker versjonen **uten** `-chatgpt-inspired`-suffikset, så du får
`nimayomp-chatgpt-inspired-3.0.42` og ikke dobbelt suffiks.

---

## Konfigurasjon

`release-manifest.json` definerer hvor kilden ligger, hvordan versjonen
leses, og hvilke artefakter som kopieres. Endre stiene der hvis
projektmappene flyttes.

| Komponent | Kilde | Versjon leses fra |
|---|---|---|
| `bridge` | `C:\Prosjekter\Nimayworkingbridgeinstaller` | `bridge\omp-bridge.mjs` → `const VERSION` |
| `android` | `C:\Prosjekter\Nimayomp\android-source\nimayomp` | `app\build.gradle` → flavor `chstgpt` |
| `linux` | `C:\Prosjekter\Nimayomp\linux-bridge` | `packaging\deb\DEBIAN\control` → `Version:` |

---

## Sikkerhet

- Bro-konfigurasjonen skrives ut som `config.template.json` med tokenet
  erstattet av `__TOKEN__`. Ingen release-mappe inneholder et gyldig token.
- `node_modules` utelates fra release-mapper; bygges ved installasjon.
- `New-Release.ps1` overskriver aldri en eksisterende release-mappe.

---

## Krav for bygging

| Hva | Krav |
|---|---|
| Android APK | Android SDK (`C:\android`), JDK 17, keystore |
| `.deb` | WSL Debian med `dpkg-deb` |
| `.rpm` | WSL med `rpmbuild` |
| Bro | Node.js 20+ |

Byggingen av Linux-pakker skjer i en Linux-native mappe i WSL (`/tmp`), fordi
filer mountet fra `/mnt/c` ikke kan få korrekte rettigheter for `dpkg-deb`.

---

## Migrering av gamle artefakter

```powershell
.\Import-Existing.ps1                    # alle komponenter
.\Import-Existing.ps1 -Component android # bare én
```

Flytter allerede bygde APK-er og Linux-pakker fra den gamle strukturen inn i
release-arkivet. Kildemappene beholdes, så ingenting går tapt.
