<#>
.SYNOPSIS
    Installerer Nimay OMP Bridge v2.9.25 med automatisk oppstart og OMP kommandoen.
.DESCRIPTION
    Dette scriptet installerer OMP Bridge i brukerens lokale programmappe
    (%LOCALAPPDATA%\Nimay\OMPBridge) og oppretter en oppgavetidplan for
    automatisk oppstart ved pålogging. Krever IKKE Administrator-rettigheter.

    Det installerer også /ompserver og /ompqr kommandoene i OMP.
    Versjon: 2.9.25
    Versjon: 2.9.25
    Port: 7778
    Kjører som: nåværende bruker (Task Scheduler)
#>

param(
    [string]$InstallDir = "$env:LOCALAPPDATA\Nimay\OMPBridge",
    [int]$Port = 7778,
    [switch]$ForceReinstall,
    [switch]$Uninstall
)

$ErrorActionPreference = "Stop"

# Farger for output
function Write-Info  { Write-Host "[INFO]  $args" -ForegroundColor Cyan }
function Write-Ok    { Write-Host "[OK]    $args" -ForegroundColor Green }
function Write-Warn  { Write-Host "[WARN]  $args" -ForegroundColor Yellow }
function Write-Error { Write-Host "[ERROR] $args" -ForegroundColor Red }

$serviceName = "NimayOMPBridge"
$taskName = "NimayOMPBridge"
$taskDescription = "Bridge between OMP on PC and OmpChat on Android (port $Port)"

# Kilde-katalog (hvor dette scriptet ligger)
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$sourceBridgeDir = Join-Path $scriptDir "bridge"
$sourceCommandsDir = Join-Path $scriptDir "commands"
$sourceToolsDir = Join-Path $scriptDir "tools"

Write-Info "Kilde: $scriptDir"
Write-Info "Installasjonsmappe: $InstallDir"
Write-Info "Port: $Port"

if ($Uninstall) {
    Write-Info "Avinstallerer..."

    # Fjern oppgavetidplan
    schtasks /Delete /TN $taskName /F 2>$null | Out-Null
    Write-Ok "Oppgavetidplan fjernet: $taskName"

    # Stopp eventuell kjørende bridge
    Get-Process node -ErrorAction SilentlyContinue | Where-Object {
        $_.Path -like "*OMPBridge*"
    } | Stop-Process -Force -ErrorAction SilentlyContinue

    # Fjern installasjonsmappe
    if (Test-Path $InstallDir) {
        Remove-Item -Recurse -Force $InstallDir
        Write-Ok "Installasjonsmappe fjernet: $InstallDir"
    }

    # Fjern OMP kommandoer
    $commandsRoot = Join-Path $env:USERPROFILE ".omp\agent\commands"
    foreach ($name in @("ompchat-shared", "ompserver", "ompqr")) {
        $target = Join-Path $commandsRoot $name
        if (Test-Path $target) {
            Remove-Item -Recurse -Force $target
            Write-Ok "Fjernet OMP kommando: $name"
        }
    }

    # Fjern state-fil
    $stateFile = Join-Path $env:USERPROFILE ".omp\agent\ompchat-commands.json"
    if (Test-Path $stateFile) {
        Remove-Item -Force $stateFile
        Write-Ok "Fjernet state-fil."
    }

    Write-Ok "Avinstallasjon fullført."
    exit 0
}

# --- INSTALLASJON ---

# 1. Sjekk Node.js
$node = (Get-Command node -ErrorAction SilentlyContinue).Source
if (-not $node) {
    foreach ($candidate in @("C:\Program Files\nodejs\node.exe", (Join-Path $env:LOCALAPPDATA "Programs\nodejs\node.exe"))) {
        if (Test-Path $candidate) { $node = $candidate; break }
    }
}
if (-not $node) {
    Write-Error "Node.js er ikke installert. Last ned fra https://nodejs.org/"
    exit 1
}
Write-Ok "Node.js funnet: $node"

# 2. Opprett installasjonsmappe (ikke admin nødvendig under LOCALAPPDATA)
if ($ForceReinstall -and (Test-Path $InstallDir)) {
    Write-Warn "Fjerner eksisterende installasjon..."
    Remove-Item -Recurse -Force $InstallDir
}
New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
Write-Ok "Installasjonsmappe opprettet: $InstallDir"

# 3. Kopier bridge-filer
# Get-ChildItem | Copy-Item -Recurse lager nested-mapper ("tools\tools")
# når målmappen allerede inneholder en mappe med samme navn. Derfor
# kopieres innholdet eksplisitt.
# Node_modules kopieres bare når -IncludeNodeModules er satt: bridge-mappen
# får avhengighetene sine via "npm install" senere, mens tools-mappen
# trenger qrcode (brukt av qrgen.mjs) og får ingen npm-install.
function Copy-TreeFlat {
    param([string]$Source, [string]$Destination, [switch]$IncludeNodeModules, [string[]]$Exclude = @())
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
    Get-ChildItem $Source -Recurse -Force | Where-Object {
        ($IncludeNodeModules -or $_.FullName -notmatch '[\\/]node_modules([\\/]|$)') -and
        ($Exclude -notcontains $_.Name)
    } | ForEach-Object {
        $rel = $_.FullName.Substring($Source.Length).TrimStart('\', '/')
        $target = Join-Path $Destination $rel
        if ($_.PSIsContainer) {
            New-Item -ItemType Directory -Path $target -Force | Out-Null
        } else {
            New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
            Copy-Item $_.FullName $target -Force
        }
    }
}

$targetBridgeDir = Join-Path $InstallDir "bridge"

# En reinstallering over en gammel installasjon skal ikke etterlate
# foreldrede filer (f.eks. en tidligere "bridge\bridge"-mappe fra en
# feilaktig kopiering). Mappen tømmes derfor for de nye filene legges inn.
# Token og port sikres likevel, se under.
$existingConfig = $null
$existingConfigFile = Join-Path $targetBridgeDir "config.json"
if (Test-Path $existingConfigFile) {
    try { $existingConfig = Get-Content $existingConfigFile -Raw -Encoding UTF8 | ConvertFrom-Json } catch { }
}

if (Test-Path $targetBridgeDir) {
    Get-ChildItem $targetBridgeDir -Force | Where-Object {
        $_.Name -notin @('config.json', 'omp-bin.txt', 'bridge.pid')
    } | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
    Write-Info "Ryddet foreldrede filer i $targetBridgeDir"
}

# config.json i kilden er en mal med plassholder-token. Den kopieres
# ikke, men genereres nedenfor ut fra config.template.json.
Copy-TreeFlat -Source $sourceBridgeDir -Destination $targetBridgeDir -Exclude @('config.json')
Write-Ok "Bridge-filer kopiert til: $targetBridgeDir"

# 4. Oppdater config.json med riktig port
$configFile = Join-Path $targetBridgeDir "config.json"
$configTemplate = Join-Path $sourceBridgeDir "config.template.json"
if (-not (Test-Path $configFile)) {
    # Fersk installasjon: start fra malen.
    if (Test-Path $configTemplate) {
        Copy-Item $configTemplate $configFile -Force
    } else {
        $obj = [pscustomobject]@{
            port = $Port; host = '0.0.0.0'; token = '__TOKEN__'
            approvalMode = 'always-ask'; allowBash = $true
            projectRoots = @(); maxChildren = 4; idleMinutes = 90
        }
        $obj | ConvertTo-Json -Depth 10 | Set-Content $configFile -Encoding UTF8
    }
}
if (Test-Path $configFile) {
    $config = Get-Content $configFile -Raw | ConvertFrom-Json
    $config.port = $Port
    if ($existingConfig -and $existingConfig.token) {
        # Behold tokenet og de eksisterende innstillingene fra den gamle
        # installasjonen; bare porten foelger -Port-parameteren.
        $config.token = $existingConfig.token
        Write-Ok "Beholdt eksisterende token fra forrige installasjon"
    } elseif ($config.token -eq '__TOKEN__' -or -not $config.token) {
        # Malen inneholder en plassholder. Ved foerste installasjon
        # genereres et ekte token her.
        $bytes = New-Object byte[] 32
        [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
        $config.token = -join ($bytes | ForEach-Object { $_.ToString('x2') })
        Write-Ok "Generert nytt paringstoken"
    }
    $config | ConvertTo-Json -Depth 10 | Set-Content $configFile -Encoding UTF8
    Write-Ok "Config oppdatert med port $Port"
}

# 4a. Skriv omp-bin.txt slik at broen alltid finner den faktiske omp.exe
# uten å være avhengig av PATH eller brukerens OMP_BIN-miljøvariabel.
# Uten denne fila faller broen tilbake på en bun-shim-sti som ikke finnes,
# og alle spawn-kall feiler med ENOENT.
$ompBin = $null
$ompCmd = Get-Command omp -ErrorAction SilentlyContinue
if ($ompCmd -and $ompCmd.Source -and (Test-Path $ompCmd.Source)) {
    $ompBin = $ompCmd.Source
} else {
    foreach ($candidate in @(
        (Join-Path $env:LOCALAPPDATA "omp\omp.exe"),
        (Join-Path $env:USERPROFILE "AppData\Local\omp\omp.exe"),
        (Join-Path $env:APPDATA "npm\omp.exe")
    )) {
        if (Test-Path $candidate) { $ompBin = $candidate; break }
    }
}
if ($ompBin) {
    Set-Content -Path (Join-Path $targetBridgeDir "omp-bin.txt") -Value $ompBin -Encoding ASCII -NoNewline
    Write-Ok "Skrev omp-bin.txt: $ompBin"
} else {
    Write-Warn "Fant ingen omp.exe - broen vil loese omp fra PATH ved oppstart."
}

# 4b. Kopier tools-mappen (nødvendig for /ompqr)
# qrgen.mjs importerer "qrcode", som ligger i tools/node_modules. Det
# kobles ikke til noe npm-install her, så node_modules må følge med.
$targetToolsDir = Join-Path $InstallDir "tools"
Copy-TreeFlat -Source $sourceToolsDir -Destination $targetToolsDir -IncludeNodeModules
Write-Ok "Tools-filer kopiert til: $targetToolsDir"

# 5. Installer NPM-avhengigheter i bridge-mappen (ikke kritisk, kan manglere package.json)
Write-Info "Installerer NPM-avhengigheter (valgfritt)..."
Push-Location $targetBridgeDir
if (Test-Path "package.json") {
    npm install --omit=dev 2>&1 | ForEach-Object { Write-Host "  $_" }
} else {
    Write-Warn "Ingen package.json funnet, hopper over NPM-installasjon"
}
Pop-Location

# 6. Opprett wrapper-script for oppgavetidplanen
$wrapperPath = Join-Path $InstallDir "bridge-start.cmd"
$wrapperContent = @"
@echo off
set "HOME=$env:USERPROFILE"
set "USERPROFILE=$env:USERPROFILE"
set "OMPCHAT_BRIDGE_PORT=$Port"
$(if ($ompBin) { "set `"OMP_BIN=$ompBin`"" })
cd /d "$targetBridgeDir"
"$node" omp-bridge.mjs
"@
Set-Content -Path $wrapperPath -Value $wrapperContent -Encoding ASCII
Write-Ok "Wrapper-script opprettet: $wrapperPath"

# 7. Opprett oppgavetidplan for auto-start ved pålogging (ikke admin nødvendig)
$taskXml = @"
<?xml version="1.0" encoding="UTF-16"?>
<Task version="1.2" xmlns="http://schemas.microsoft.com/windows/2004/02/mit/task">
  <RegistrationInfo>
    <Date>$(Get-Date -Format "yyyy-MM-ddTHH:mm:ss")</Date>
    <Author>$env:USERDOMAIN\$env:USERNAME</Author>
    <Description>$taskDescription</Description>
  </RegistrationInfo>
  <Triggers>
    <LogonTrigger>
      <Enabled>true</Enabled>
      <UserId>$env:USERDOMAIN\$env:USERNAME</UserId>
      <Delay>PT30S</Delay>
    </LogonTrigger>
  </Triggers>
  <Principals>
    <Principal id="Author">
      <UserId>$env:USERDOMAIN\$env:USERNAME</UserId>
      <LogonType>InteractiveToken</LogonType>
      <RunLevel>LeastPrivilege</RunLevel>
    </Principal>
  </Principals>
  <Settings>
    <MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy>
    <DisallowStartIfOnBatteries>false</DisallowStartIfOnBatteries>
    <StopIfGoingOnBatteries>false</StopIfGoingOnBatteries>
    <AllowHardTerminate>true</AllowHardTerminate>
    <StartWhenAvailable>false</StartWhenAvailable>
    <RunOnlyIfNetworkAvailable>false</RunOnlyIfNetworkAvailable>
    <IdleSettings>
      <StopOnIdleEnd>true</StopOnIdleEnd>
      <RestartOnIdle>false</RestartOnIdle>
    </IdleSettings>
    <AllowStartOnDemand>true</AllowStartOnDemand>
    <Enabled>true</Enabled>
    <Hidden>false</Hidden>
    <RunOnlyIfIdle>false</RunOnlyIfIdle>
    <WakeToRun>false</WakeToRun>
    <ExecutionTimeLimit>PT0S</ExecutionTimeLimit>
    <Priority>7</Priority>
  </Settings>
  <Actions Context="Author">
    <Exec>
      <Command>$wrapperPath</Command>
      <WorkingDirectory>$targetBridgeDir</WorkingDirectory>
    </Exec>
  </Actions>
</Task>
"@

$tempXml = Join-Path $env:TEMP "NimayOMPBridge-task.xml"
[System.IO.File]::WriteAllText($tempXml, $taskXml, (New-Object System.Text.UTF8Encoding($false)))

schtasks /Create /TN $taskName /XML $tempXml /F 2>&1 | Out-Null
Remove-Item $tempXml -Force -ErrorAction SilentlyContinue
Write-Ok "Oppgavetidplan opprettet: $taskName (starter ved pålogging)"

# 8. Installer OMP kommandoer (/ompserver, /ompqr)
Write-Info "Installerer OMP kommandoer..."
$commandsRoot = Join-Path $env:USERPROFILE ".omp\agent\commands"
New-Item -ItemType Directory -Force -Path $commandsRoot | Out-Null

$installed = @()
foreach ($name in @("ompchat-shared", "ompserver", "ompqr")) {
    $source = Join-Path $sourceCommandsDir $name
    if (-not (Test-Path $source)) {
        Write-Warn "Hopper over (mangler): $source"
        continue
    }
    $target = Join-Path $commandsRoot $name
    if (Test-Path $target) { Remove-Item -Recurse -Force $target }
    Copy-Item -Recurse -Force $source $target
    $installed += $name
    Write-Ok "Installert OMP kommando: $name"
}

# 9. Skriv state-fil (peker på installasjonsmappe)
$stateFile = Join-Path $env:USERPROFILE ".omp\agent\ompchat-commands.json"
$json = @{ 
    projectRoot = $InstallDir
    bridge = (Join-Path $targetBridgeDir "omp-bridge.mjs")
} | ConvertTo-Json
[System.IO.File]::WriteAllText($stateFile, $json, (New-Object System.Text.UTF8Encoding($false)))
Write-Ok "State-fil skrevet: $stateFile"

# 10. Verifiser at bridge svarer på port
Write-Info "Venter på bridge å bli klar på port $Port..."
# Ruten heter /api/health (ikke /health), og krever Authorization-header.
# Uten token ville kontrollen alltid feilet, selv om broen startet fint.
$bridgeToken = $null
if (Test-Path $configFile) {
    try { $bridgeToken = (Get-Content $configFile -Raw | ConvertFrom-Json).token } catch { }
}
$healthHeaders = @{}
if ($bridgeToken) { $healthHeaders['Authorization'] = "Bearer $bridgeToken" }

$ready = $false
for ($i = 0; $i -lt 30; $i++) {
    try {
        $response = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/api/health" -Headers $healthHeaders -TimeoutSec 2 -UseBasicParsing -ErrorAction Stop
        if ($response.StatusCode -eq 200) {
            $ready = $true
            break
        }
    } catch { }
    Start-Sleep -Seconds 1
}

if ($ready) {
    Write-Ok "Bridge svarer på http://127.0.0.1:$Port/api/health"
} else {
    Write-Warn "Bridge svarer ikke ennå på port $Port (kan ta litt tid første gang)"
}

# Oppsummering
Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  INSTALLASJON FULLFØRT" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Versjon:      2.9.25
Write-Host "Port:         $Port"
Write-Host "Installert i: $InstallDir"
Write-Host "Auto-start:   Oppgavetidplan '$taskName' (ved pålogging)"
Write-Host "Kjører som:   $env:USERDOMAIN\$env:USERNAME"
Write-Host ""
Write-Host "OMP kommandoer installert:" -ForegroundColor Cyan
foreach ($c in $installed) { Write-Host "  /$c" }
Write-Host ""
Write-Host "Bruk i OMP:" -ForegroundColor Cyan
Write-Host "  /ompserver  - Vis bridge-status og administrer"
Write-Host "  /ompqr      - Generer QR-kode for paring med Android"
Write-Host ""
Write-Host "Kjør bridge manuelt:" -ForegroundColor Cyan
Write-Host "  $wrapperPath"
Write-Host ""
Write-Host "Avinstaller:" -ForegroundColor Cyan
Write-Host "  .\Install-OMPBridge.ps1 -Uninstall"
Write-Host "============================================" -ForegroundColor Green