<#
.SYNOPSIS
    Starter Nimay OMP Bridge på nytt uten tap av tilgang.

.DESCRIPTION
    Windows: En ny bro startes på en parallell port mens den gamle fortsatt
    kjører. Den nye verifiseres (health, authentisering, spawn av omp,
    modellliste). Først når den er frisk, stoppes den gamle og porten
    overtas. Ved feil rulles tilbake til den gamle.

    Dette er samme modell som AGENTS.md beskriver for port 7799.

    Linux: Tjenesten startes på nytt med systemctl restart, og det verifiseres
    at den svarer etterpå.

    Eksempel:
        .\Restart-Bridge.ps1 -Port 7778
        .\Restart-Bridge.ps1 -Port 7778 -Linux
        .\Restart-Bridge.ps1 -Port 7778 -SkipLinux
#>
[CmdletBinding()]
param(
    [int]$Port = 7778,
    [int]$StagingPort = 7799,
    [switch]$Linux,
    [switch]$SkipLinux,
    # Bruk parallell-porten og oppdater config.json, naar den ekte porten
    # holdes av en prosess som ikke kan avsluttes uten admin.
    [switch]$UseStagingPort,
    [switch]$WhatIfOnly
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$installDir = Join-Path $env:LOCALAPPDATA 'Nimay\OMPBridge'
$bridgeDir = Join-Path $installDir 'bridge'
$configFile = Join-Path $bridgeDir 'config.json'

function Write-Step($msg) { Write-Host "  $msg" -ForegroundColor Cyan }
function Write-Ok($msg) { Write-Host "  OK  $msg" -ForegroundColor Green }
function Write-Bad($msg) { Write-Host "  FEIL $msg" -ForegroundColor Red }

function Get-BridgeToken {
    if (-not (Test-Path $configFile)) { return $null }
    try { return (Get-Content $configFile -Raw -Encoding UTF8 | ConvertFrom-Json).token } catch { return $null }
}

function Test-Bridge {
    param([int]$P, [string]$Token)
    if (-not $Token) { return $false }
    try {
        $r = Invoke-RestMethod -Uri "http://127.0.0.1:$P/api/health" `
            -Headers @{ Authorization = "Bearer $Token" } -TimeoutSec 3
        return ($r.ok -eq $true -and $r.version)
    } catch { return $false }
}

Write-Host ""
Write-Host "NIMAY OMP BRIDGE - RESTART" -ForegroundColor Cyan
Write-Host ("=" * 56) -ForegroundColor DarkGray

# ============================================================================
# WINDOWS
# ============================================================================
if (-not $SkipLinux -or $true) {
    Write-Host "Windows (port $Port)" -ForegroundColor Cyan

    if (-not (Test-Path (Join-Path $bridgeDir 'omp-bridge.mjs'))) {
        Write-Bad "Fant ingen installasjon i $installDir. Kjør Install-All.ps1 først."
        exit 1
    }

    $token = Get-BridgeToken
    if (-not $token) { Write-Bad "Klarer ikke lese token fra config.json."; exit 1 }

    $currentVersion = (Select-String -Path (Join-Path $bridgeDir 'omp-bridge.mjs') -Pattern 'const VERSION = "([^"]+)"' |
        Select-Object -First 1).Matches.Groups[1].Value
    Write-Step "Installert versjon: $currentVersion"

    $wasRunning = Test-Bridge -P $Port -Token $token
    Write-Step "Port $Port er $(if ($wasRunning) { 'i drift' } else { 'IKKE i drift' })"

    if ($WhatIfOnly) {
        Write-Host ""
        Write-Host "Dry-run: ville startet ny bro pa staging-port $StagingPort, verifisert den, og deretter overtatt port $Port." -ForegroundColor Yellow
        Write-Host ""
        return
    }

    # --- Steg 1: start staging-instans på parallell port -----------------------
    Write-Step "Starter staging-instans pa port $StagingPort ..."
    $stageDir = Join-Path $env:LOCALAPPDATA "Nimay\OMPBridge-Staging-$StagingPort"
    $stageBridge = Join-Path $stageDir 'bridge'

    # Stopp en evtuel tidligere staging-instans.
    $stagePidFile = Join-Path $stageBridge 'bridge.pid'
    if (Test-Path $stagePidFile) {
        $raw = (Get-Content $stagePidFile -Raw).Trim()
        $pid2 = 0
        if ([int]::TryParse($raw, [ref]$pid2)) {
            Stop-Process -Id $pid2 -Force -ErrorAction SilentlyContinue
            Start-Sleep -Seconds 2
        }
    }

    # Bygg staging-katalogen fra den installerte broen.
    # Staging ligger i en egen mappe, slik at den ikke skriver over
    # bridge.pid i installasjonen (den filen deles av flere porter).
    Remove-Item $stageDir -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Path $stageBridge -Force | Out-Null
    Copy-Item (Join-Path $bridgeDir 'omp-bridge.mjs') $stageBridge -Force
    if (Test-Path (Join-Path $bridgeDir 'omp-bin.txt')) {
        Copy-Item (Join-Path $bridgeDir 'omp-bin.txt') $stageBridge -Force
    }
    # Samme token, men egen port - appen kan ikke koble seg til staging.
    $stageConfig = Get-Content $configFile -Raw -Encoding UTF8 | ConvertFrom-Json
    $stageConfig.port = $StagingPort
    $stageConfig | ConvertTo-Json -Depth 10 | Set-Content (Join-Path $stageBridge 'config.json') -Encoding UTF8

    $node = (Get-Command node -ErrorAction SilentlyContinue).Source
    if (-not $node) { $node = 'C:\Program Files\nodejs\node.exe' }

    $stageEnv = @{
        HOME = $env:USERPROFILE
        USERPROFILE = $env:USERPROFILE
        OMPCHAT_BRIDGE_PORT = "$StagingPort"
    }
    if (Test-Path (Join-Path $bridgeDir 'omp-bin.txt')) {
        $stageEnv['OMP_BIN'] = (Get-Content (Join-Path $bridgeDir 'omp-bin.txt') -Raw).Trim()
    }
    $stageOut = Join-Path $stageBridge 'bridge.stdout.log'
    $stageErr = Join-Path $stageBridge 'bridge.stderr.log'
    $stageProc = Start-Process -FilePath $node -ArgumentList "`"$stageBridge\omp-bridge.mjs`"" `
        -WorkingDirectory $stageBridge -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $stageOut -RedirectStandardError $stageErr

    # --- Steg 2: verifiser staging -------------------------------------------
    $stageOk = $false
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Milliseconds 500
        if (Test-Bridge -P $StagingPort -Token $token) { $stageOk = $true; break }
    }

    if (-not $stageOk) {
        Write-Bad "Staging-instansen startet ikke. Ruller ikke tilbake - den gamle kjører fortsatt."
        if (Test-Path $stageErr) { Get-Content $stageErr -Tail 10 | ForEach-Object { "      $_" } }
        exit 1
    }
    Write-Ok "Staging svarer pa port $StagingPort"

    # Verifiser at omp faktisk kan startes (dette var ENOENT-feilen).
    $spawnOk = $false
    try {
        $body = @{ cwd = $env:USERPROFILE; text = 'ping' } | ConvertTo-Json
        $r = Invoke-WebRequest -Uri "http://127.0.0.1:$StagingPort/api/prompt" -Method POST `
            -Headers @{ Authorization = "Bearer $token"; 'Content-Type' = 'application/json' } `
            -Body $body -TimeoutSec 90 -UseBasicParsing
        $spawnOk = ($r.StatusCode -eq 200)
    } catch {
        $spawnOk = $false
    }
    if ($spawnOk) { Write-Ok "omp startes fra staging (spawn OK)" }
    else { Write-Bad "omp kunne ikke startes fra staging" }

    $modelOk = $false
    try {
        $m = Invoke-RestMethod -Uri "http://127.0.0.1:$StagingPort/api/models?cwd=$($env:USERPROFILE -replace '\\','/')" `
            -Headers @{ Authorization = "Bearer $token" } -TimeoutSec 60
        $modelOk = ($m.models.Count -gt 0)
    } catch { $modelOk = $false }
    if ($modelOk) { Write-Ok "Modellliste hentet" } else { Write-Bad "Kunne ikke hente modellliste" }

    if (-not ($spawnOk -and $modelOk)) {
        Write-Bad "Staging bestod ikke verifikasjon. Stopper staging, lar gammel bro kjøre."
        Stop-Process -Id $stageProc.Id -Force -ErrorAction SilentlyContinue
        exit 1
    }

    # --- Steg 3: overta porten -----------------------------------------------
    # En port kan holdes av en prosess fra en tidligere installasjon som
    # kjoerer som SYSTEM (tjenesten NimayOMPBridge). Da stopper pid-filen
    # ikke samsvare med prosessen, og porten kan ikke frigjoeres uten
    # administrator-rettigheter. I sa fall startes den nye broen pa en
    # parallell port, og appen far den nye porten i config.
    $portHeldByForeign = $false
    $foreignPid = 0

    Write-Step "Stopper gammel instans pa port $Port ..."

    # Stopp bare prosessen som faktisk holder porten. bridge.pid er
    # per-installasjon og deles av flere porter, saa den kan peke pa en
    # helt annen bro. Å stoppe den ville tatt ned feil instans.
    $holder = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if ($holder) {
        $oldPid = $holder.OwningProcess
        $p = Get-CimInstance Win32_Process -Filter "ProcessId = $oldPid" -ErrorAction SilentlyContinue
        if ($p -and $p.Name -eq 'node.exe' -and $p.CommandLine -like "*omp-bridge.mjs*") {
            try {
                Stop-Process -Id $oldPid -Force -ErrorAction Stop
                Write-Ok "Stoppet broen pa port $Port (PID $oldPid)"
            } catch {
                Write-Bad "Kunne ikke stoppe PID $oldPid : $($_.Exception.Message)"
            }
        } else {
            Write-Step "PID $oldPid er ikke en omp-bridge (kan vaere SYSTEM) - stopper ikke."
        }
    } else {
        Write-Step "Ingen prosess holder port $Port."
    }

    # Sjekk om porten fortsatt er ok, og hvem som holder den.
    Start-Sleep -Seconds 2
    $holder = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if ($holder) {
        $foreignPid = $holder.OwningProcess
        $portHeldByForeign = $true
        Write-Bad "Port $Port holdes fortsatt av PID $foreignPid (kan vaere en SYSTEM-prosess fra en gammel installasjon)."
    }

    # Stans staging slik at porten blir fri.
    Stop-Process -Id $stageProc.Id -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2

    if ($portHeldByForeign) {
        # Porten holdes av en prosess fra en gammel installasjon som kjører
        # som SYSTEM. Den kan ikke avsluttes uten administrator-rettigheter,
        # og porten kan derfor ikke overtas.
        if (-not $UseStagingPort) {
            #
            # Dette endres IKKE automatisk: config.json sin port er noe
            # mobilappen parer mot, og den skal ikke flyttes uten at det er
            # et bevisst valg.
            Write-Bad "Port $Port holdes av PID $foreignPid (SYSTEM) fra en gammel installasjon."
            Write-Host ""
            Write-Host "  Kan ikke frigjoere porten uten administrator-rettigheter." -ForegroundColor Yellow
            Write-Host "  Dette gaar normalt over ved omstart: tjenesten NimayOMPBridge" -ForegroundColor Yellow
            Write-Host "  starter broen pa nytt med riktig versjon." -ForegroundColor Yellow
            Write-Host ""
            Write-Host "  Vil du bruke parallell-porten $StagingPort i mellomtiden?" -ForegroundColor Yellow
            Write-Host "    .\Restart-Bridge.ps1 -Port $Port -StagingPort $StagingPort -UseStagingPort" -ForegroundColor Yellow
            Write-Host ""
            exit 1
        }

        # Eksplisitt valgt: flytt configen til parallell-porten og start
        # broen derfra. Den overlever omstart, fordi oppgaven peker pa
        # bridge-start.cmd, som leser porten fra config.json.
        Write-Step "Bruker parallell-porten $StagingPort (valgt eksplisitt)."
        $stageConfig.port = $StagingPort
        $stageConfig | ConvertTo-Json -Depth 10 | Set-Content (Join-Path $bridgeDir 'config.json') -Encoding UTF8

        $finalProc = Start-Process -FilePath $node -ArgumentList "`"$bridgeDir\omp-bridge.mjs`"" `
            -WorkingDirectory $bridgeDir -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput (Join-Path $bridgeDir 'bridge.stdout.log') `
            -RedirectStandardError (Join-Path $bridgeDir 'bridge.stderr.log')

        $started = $false
        for ($i = 0; $i -lt 40; $i++) {
            Start-Sleep -Milliseconds 500
            if (Test-Bridge -P $StagingPort -Token $token) { $started = $true; break }
        }
        if (-not $started) {
            Write-Bad "Broen startet ikke pa $StagingPort."
            exit 1
        }
        Write-Ok "Bro kjører pa port $StagingPort (config.json peker dit)"
        Write-Host ""
        Write-Host "  Port $Port holdes fortsatt av PID $foreignPid (SYSTEM)." -ForegroundColor Yellow
        Write-Host ""
        exit 0
    }
    # Start den nye broen pa den ekte porten.
    # config.json bestemmer hvilken port broen lytter pa. Flere porter
    # deler samme installasjon, saa porten settes til den som skal
    # betjenes, og den gamle porten settes tilbake etterpå.
    Write-Step "Starter ny bro pa port $Port ..."
    $liveConfig = Get-Content $configFile -Raw -Encoding UTF8 | ConvertFrom-Json
    $previousPort = $liveConfig.port
    if ($previousPort -ne $Port) {
        $liveConfig.port = $Port
        $liveConfig | ConvertTo-Json -Depth 10 | Set-Content $configFile -Encoding UTF8
        Write-Step "Sat config.json port til $Port (var $previousPort)"
    }

    $newProc = Start-Process -FilePath $node -ArgumentList "`"$bridgeDir\omp-bridge.mjs`"" `
        -WorkingDirectory $bridgeDir -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $bridgeDir 'bridge.stdout.log') `
        -RedirectStandardError (Join-Path $bridgeDir 'bridge.stderr.log')

    $started = $false
    for ($i = 0; $i -lt 40; $i++) {
        Start-Sleep -Milliseconds 500
        if (Test-Bridge -P $Port -Token $token) { $started = $true; break }
    }

    if (-not $started) {
        Write-Bad "Ny bro startet ikke pa port $Port."
        # Sett config-porten tilbake, slik at en feil ikke etterlater
        # installasjonen i en tilstand der ingen bro matcher configen.
        if ($previousPort -and $previousPort -ne $Port) {
            try {
                $restore = Get-Content $configFile -Raw -Encoding UTF8 | ConvertFrom-Json
                $restore.port = $previousPort
                $restore | ConvertTo-Json -Depth 10 | Set-Content $configFile -Encoding UTF8
                Write-Step "Satte config.json port tilbake til $previousPort"
            } catch { }
        }
        Write-Host ""
        Write-Host "  Opprett en oppgavetidplan for a starte broen ved paaloggning:" -ForegroundColor Yellow
        Write-Host "  Set-ScheduledTask -TaskName 'NimayOMPBridge' -Action (New-ScheduledTaskAction -Execute '$installDir\bridge-start.cmd' -WorkingDirectory '$bridgeDir')" -ForegroundColor Yellow
        exit 1
    }
    Write-Ok "Ny bro kjører pa port $Port"

    # Rydd staging-katalogen.
    Remove-Item $stageDir -Recurse -Force -ErrorAction SilentlyContinue

    # Sett config-porten tilbake til den opprinnelige porten, slik at
    # installasjonen fortsatt matcher den porten mobilappen er paret mot.
    # Broen pa $Port fortsetter aa kjoere; den leser porten ved oppstart.
    if ($previousPort -and $previousPort -ne $Port) {
        try {
            $restore = Get-Content $configFile -Raw -Encoding UTF8 | ConvertFrom-Json
            $restore.port = $previousPort
            $restore | ConvertTo-Json -Depth 10 | Set-Content $configFile -Encoding UTF8
            Write-Step "Satte config.json port tilbake til $previousPort"
        } catch { }
    }
    Write-Host ""
}

# ============================================================================
# LINUX
# ============================================================================
if ($Linux) {
    Write-Host "Linux (systemd)" -ForegroundColor Cyan
    Write-Step "Kjoerer systemctl restart nimay-omp-bridge ..."
    $out = wsl.exe -u root -e sh -lc "systemctl restart nimay-omp-bridge && sleep 3 && systemctl is-active nimay-omp-bridge" 2>&1
    $active = ($out | Select-Object -Last 1).ToString().Trim()
    if ($active -eq 'active') { Write-Ok "Tjenesten er active" }
    else { Write-Bad "Tjenesten er ikke active ($active)"; exit 1 }

    # Verifiser at broen svarer.
    $cfg = wsl.exe -e sh -lc "cat /opt/nimay-omp-bridge/config.json" 2>&1 | Out-String
    try {
        $linuxToken = ($cfg | ConvertFrom-Json).token
        $linuxPort = ($cfg | ConvertFrom-Json).port
        $lr = Invoke-RestMethod -Uri "http://127.0.0.1:$linuxPort/api/health" `
            -Headers @{ Authorization = "Bearer $linuxToken" } -TimeoutSec 10
        Write-Ok "Bro svarer pa port $linuxPort (v$($lr.version))"
    } catch {
        Write-Bad "Bro svarer ikke: $($_.Exception.Message)"
        exit 1
    }
    Write-Host ""
}

Write-Host "Ferdig." -ForegroundColor Green
Write-Host ""
