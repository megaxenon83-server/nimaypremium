# start-bridge-service.ps1
# Installerer/fjerner Nimay OMP Bridge som Windows-tjeneste (SYSTEM).
# Bruk: -Install eller -Uninstall

param(
    [switch]$Install,
    [switch]$Uninstall
)

$serviceName = "NimayOMPBridge"
$serviceDisplayName = "Nimay OMP Bridge"
$serviceDescription = "Bridge between OMP on PC and OmpChat on Android"

$appDir = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$bridgeDir = Join-Path $appDir "bridge"
$script = Join-Path $bridgeDir "omp-bridge.mjs"

# Finn brukerens profil (for HOME/USERPROFILE)
$userProfiles = Get-ChildItem "C:\Users" -Directory -ErrorAction SilentlyContinue | Where-Object {
    (Test-Path (Join-Path $_.FullName ".omp")) -or (Test-Path (Join-Path $_.FullName ".ompchat"))
}
$profilePath = if ($userProfiles) {
    ($userProfiles | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
} else {
    $env:USERPROFILE
}

# Finn node
$node = (Get-Command node -ErrorAction SilentlyContinue).Source
if (-not $node) {
    foreach ($candidate in @("C:\Program Files\nodejs\node.exe", (Join-Path $env:LOCALAPPDATA "Programs\nodejs\node.exe"))) {
        if (Test-Path $candidate) { $node = $candidate; break }
    }
}
if (-not $node) { Write-Host "Node.js er ikke installert."; exit 1 }

if ($Uninstall) {
    # Stopp og fjern tjenesten
    $svc = Get-Service -Name $serviceName -ErrorAction SilentlyContinue
    if ($svc) {
        if ($svc.Status -eq "Running") {
            Stop-Service -Name $serviceName -Force -ErrorAction SilentlyContinue
            Start-Sleep -Seconds 2
        }
        # Bruk sc.exe for å slette
        sc.exe delete $serviceName | Out-Null
        Write-Host "Tjeneste $serviceName fjernet."
    } else {
        Write-Host "Tjeneste $serviceName finnes ikke."
    }
    exit 0
}

if ($Install) {
    # Stopp eksisterende tjeneste hvis den kjører
    $svc = Get-Service -Name $serviceName -ErrorAction SilentlyContinue
    if ($svc) {
        if ($svc.Status -eq "Running") {
            Stop-Service -Name $serviceName -Force -ErrorAction SilentlyContinue
            Start-Sleep -Seconds 2
        }
        sc.exe delete $serviceName | Out-Null
        Start-Sleep -Seconds 1
    }

    # Opprett wrapper .bat som setter miljøvariabler og starter bridge
    $wrapperPath = Join-Path $appDir "bridge-service.cmd"
    $wrapperContent = @"
@echo off
set "HOME=$profilePath"
set "USERPROFILE=$profilePath"
set "OMPCHAT_BRIDGE_PORT=7777"
cd /d "$bridgeDir"
"$node" omp-bridge.mjs
"@
    Set-Content -Path $wrapperPath -Value $wrapperContent -Encoding ASCII

    # Opprett tjenesten med sc.exe (kjører som SYSTEM, ingen passord)
    $result = sc.exe create $serviceName binPath= "`"$wrapperPath`"" start= auto obj= "NT AUTHORITY\SYSTEM" DisplayName= "$serviceDisplayName"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Kunne ikke opprette tjeneste: $result"
        exit 1
    }

    # Sett beskrivelse
    sc.exe description $serviceName "$serviceDescription" | Out-Null

    # Start tjenesten
    sc.exe start $serviceName | Out-Null
    Start-Sleep -Seconds 3

    # Verifiser
    $svc = Get-Service -Name $serviceName -ErrorAction SilentlyContinue
    if ($svc -and $svc.Status -eq "Running") {
        Write-Host "Tjeneste $serviceName opprettet og startet."
    } else {
        Write-Host "Tjeneste opprettet, men start feilet. Prov manuelt: sc start $serviceName"
    }
    exit 0
}

Write-Host "Bruk -Install eller -Uninstall"
exit 1