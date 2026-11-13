<#>
.SYNOPSIS
    Avinstallerer Nimay OMP Bridge v2.9.25.
.DESCRIPTION
    Fjerner oppgavetidplanen, installasjonsmappen (%LOCALAPPDATA%\Nimay\OMPBridge)
    og OMP-kommandoene (/ompserver, /ompqr). Krever IKKE Administrator-rettigheter.
.NOTES
    Versjon: 2.9.25
#>

param(
    [string]$InstallDir = "$env:LOCALAPPDATA\Nimay\OMPBridge",
    [switch]$Force
)

$ErrorActionPreference = "Stop"

function Write-Info  { Write-Host "[INFO]  $args" -ForegroundColor Cyan }
function Write-Ok    { Write-Host "[OK]    $args" -ForegroundColor Green }
function Write-Warn  { Write-Host "[WARN]  $args" -ForegroundColor Yellow }
function Write-Error { Write-Host "[ERROR] $args" -ForegroundColor Red }

$taskName = "NimayOMPBridge"

Write-Info "Avinstallerer Nimay OMP Bridge v2.9.25"
Write-Info "Installasjonsmappe: $InstallDir"

# 1. Fjern oppgavetidplan
Write-Info "Fjerner oppgavetidplan: $taskName..."
schtasks /Delete /TN $taskName /F 2>$null | Out-Null
if ($LASTEXITCODE -eq 0) {
    Write-Ok "Oppgavetidplan fjernet."
} else {
    Write-Warn "Ingen oppgavetidplan funnet (kan allerede være fjernet)."
}

# 2. Stopp eventuell kjørende bridge
Write-Info "Stopper eventuell kjørende bridge..."
Get-Process node -ErrorAction SilentlyContinue | Where-Object {
    $_.Path -like "*OMPBridge*"
} | Stop-Process -Force -ErrorAction SilentlyContinue
Write-Ok "Bridge-stoppet (hvis noen kjørte)."

# 3. Fjern installasjonsmappe
if (Test-Path $InstallDir) {
    Write-Info "Fjerner installasjonsmappe: $InstallDir..."
    Remove-Item -Recurse -Force $InstallDir -ErrorAction SilentlyContinue
    if (-not (Test-Path $InstallDir)) {
        Write-Ok "Installasjonsmappe fjernet."
    } else {
        Write-Warn "Kunne ikke fjerne hele mappen (filen kan være låst). Prøv igjen etter omstart."
    }
} else {
    Write-Warn "Installasjonsmappe finnes ikke."
}

# 4. Fjern OMP kommandoer
$commandsRoot = Join-Path $env:USERPROFILE ".omp\agent\commands"
$removedAny = $false
foreach ($name in @("ompchat-shared", "ompserver", "ompqr")) {
    $target = Join-Path $commandsRoot $name
    if (Test-Path $target) {
        Remove-Item -Recurse -Force $target -ErrorAction SilentlyContinue
        Write-Ok "Fjernet OMP kommando: /$name"
        $removedAny = $true
    }
}
if (-not $removedAny) {
    Write-Warn "Ingen OMP-kommandoer funnet å fjerne."
}

# 5. Fjern state-fil
$stateFile = Join-Path $env:USERPROFILE ".omp\agent\ompchat-commands.json"
if (Test-Path $stateFile) {
    Remove-Item -Force $stateFile -ErrorAction SilentlyContinue
    Write-Ok "Fjernet state-fil: ompchat-commands.json"
} else {
    Write-Warn "Ingen state-fil funnet."
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  AVINSTALLASJON FULLFØRT" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host "Nimay OMP Bridge v2.9.25 er fjernet."
Write-Host ""
Write-Host "Hvis du vil installere på nytt, kjør install.bat i foreldremappen."
Write-Host "============================================" -ForegroundColor Green