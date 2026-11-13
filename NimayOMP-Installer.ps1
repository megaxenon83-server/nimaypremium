# Nimay OMP Mobile 2.4 - Windows Installer Script
# PowerShell-baseret installer for Windows

param(
    [Parameter(Mandatory=$false)]
    [string]$InstallPath = "C:\Program Files\Nimay OMP Mobile"
)

$ErrorActionPreference = "Stop"

# Skriv ut velkomstmelding
Write-Host "========================================" -ForegroundColor Green
Write-Host "  Nimay OMP Mobile 2.4 - Installer" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Velkommen til Nimay OMP Mobile!"
Write-Host "Din egen AI-assistent på PC og mobil."
Write-Host ""

# Opprett installeringsmappe
if (!(Test-Path $InstallPath)) {
    New-Item -ItemType Directory -Path $InstallPath -Force | Out-Null
    Write-Host "Opprettet installasjonsmappe: $InstallPath" -ForegroundColor Cyan
}

# Definer kildemapper
$srcRoot = Split-Path -Parent $PSScriptRoot
$srcApk = Join-Path $srcRoot "OmpChat-2.3-release.apk"
$srcBridge = Join-Path $srcRoot "bridge"
$srcCommands = Join-Path $srcRoot "omp-commands"
$srcDocs = Join-Path $srcRoot "README.md"

# Kopier APK-filen
$dstApk = Join-Path $InstallPath "OmpChat-2.3-release.apk"
if (Test-Path $srcApk) {
    Copy-Item -Path $srcApk -Destination $dstApk -Force
    Write-Host "Kopierte APK: $dstApk" -ForegroundColor Green
} else {
    Write-Warning "Fant ikke APK-fil: $srcApk"
}

# Kopier bridge-filer
$dstBridge = Join-Path $InstallPath "bridge"
if (Test-Path $srcBridge) {
    Copy-Item -Path $srcBridge -Destination $dstBridge -Recurse -Force
    Write-Host "Kopierte bridge-filer til: $dstBridge" -ForegroundColor Green
}

# Kopier OMP-kommandoer
$dstCommands = Join-Path $InstallPath "commands"
if (Test-Path $srcCommands) {
    Copy-Item -Path $srcCommands -Destination $dstCommands -Recurse -Force
    Write-Host "Kopierte OMP-kommandoer til: $dstCommands" -ForegroundColor Green
}

# Opprett Start-meny-snarvei
$startMenuDir = Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs\Nimay OMP"
if (!(Test-Path $startMenuDir)) {
    New-Item -ItemType Directory -Path $startMenuDir -Force | Out-Null
}

$shortcutPath = Join-Path $startMenuDir "Nimay OMP Mobile.lnk"
$wshShell = New-Object -ComObject WScript.Shell
$shortcut = $wshShell.CreateShortcut($shortcutPath)
$shortcut.TargetPath = $dstApk
$shortcut.Description = "Nimay OMP Mobile - Android-chatklient for OMP-agent"
$shortcut.Save()
Write-Host "Opprettet Start-meny-snarvei: $shortcutPath" -ForegroundColor Green

# Opprett skrivebordsnarvei
$desktopDir = Join-Path $env:USERPROFILE "Desktop"
$desktopShortcut = Join-Path $desktopDir "Nimay OMP Mobile.lnk"
$shortcut2 = $wshShell.CreateShortcut($desktopShortcut)
$shortcut2.TargetPath = $dstApk
$shortcut2.Description = "Nimay OMP Mobile - Android-chatklient for OMP-agent"
$shortcut2.Save()
Write-Host "Opprettet skrivebordsnarvei: $desktopShortcut" -ForegroundColor Green

# Opprett README for brukeren
$readmePath = Join-Path $InstallPath "LES_MEG_FØRST.txt"
$readmeContent = @"
Nimay OMP Mobile 2.4 - Installasjonsinformasjon
===============================================

1. INSTALLASJONSFULLFØRT
   APK-filen er installert i: $dstApk

2. HVORDAN BRUIRE APK-PÅ ANDROID
   a. Overfør OmpChat-2.3-release.apk til Android-telefonen din
   b. Åpne filen på telefonen for å installere
   c. Tillat installasjon fra ukjente kilder hvis spurt

3. STARTE BRIDGE PÅ PC
   Åpne en terminal i mappen: $dstBridge
   Kjør: node omp-bridge.mjs

4. PARING MED MOBIL
   a. Kjør /ompserver i OMP for å starte broen
   b. Kjør /ompqr i OMP for å generere QR-kode
   c. Skann QR-koden med OmpChat-appen på telefonen

5. STØTTEDE FUNKSJONER
   - Chat med OMP-agenten på PC-en
   - Vedlegg (bilder, filer)
   - Terminal (kommandoer på PC)
   - Tale og stemme (NB-Whisper, Norsk Piper, osv.)
   - Prosjektstyring (bytt/nytt prosjekt)
   - QR-kode paring

6. SIKKERHET
   - Tilgangstoken lagres kryptert i Android Keystore
   - Ingen data sendes til eksterne servere
   - All kommunikasjon er lokal mellom PC og mobil

7. AVINSTALLERING
   Slett mappen: $InstallPath
   Slett snarveier fra Start-meny og skrivebord

Opprettet av Nimay OMP Mobile 2.4
"@
$readmeContent | Out-File -FilePath $readmePath -Encoding UTF8
Write-Host "Opprettet lese-meg-fil: $readmePath" -ForegroundColor Green

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  Installasjon fullført!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "APK-fil: $dstApk"
Write-Host "Bridge-mappe: $dstBridge"
Write-Host "Start-meny: $startMenuDir"
Write-Host "Skrivebord: $desktopShortcut"
Write-Host ""
Write-Host "Neste trinn:"
Write-Host "1. Overfør APK-filen til Android-telefonen"