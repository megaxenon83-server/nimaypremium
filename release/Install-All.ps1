<#
.SYNOPSIS
    Installerer Nimay OMP Bridge på Windows OG Linux fra den samme kilden.

.DESCRIPTION
    Kjører Windows-installasjonen (fra %LOCALAPPDATA%\Nimay\OMPBridge) og
    bygger + installerer Linux-pakker (.deb og .rpm) i WSL, med mindre
    de hoppes over.

    Eksempel:
        .\Install-All.ps1                     # Windows + Linux
        .\Install-All.ps1 -SkipLinux          # bare Windows
        .\Install-All.ps1 -SkipWindows        # bare Linux
        .\Install-All.ps1 -Port 7778 -LinuxPort 7802

    Kilden er den aktive installasjons-katalogen (2.9.25-bridge).
#>
[CmdletBinding()]
param(
    [int]$Port = 7778,
    [int]$LinuxPort = 7802,
    [switch]$SkipLinux,
    [switch]$SkipWindows,
    [switch]$BuildLinuxPackages
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$projectRoot = Split-Path -Parent $scriptDir

# Kilden: installasjons-katalogen for den aktive bridge-versjonen.
$sourceCandidates = @(
    (Join-Path $projectRoot '2.9.25-bridge'),
    'C:\Prosjekter\Nimayworkingbridgeinstaller'
)
$sourceDir = $null
foreach ($c in $sourceCandidates) {
    if (Test-Path (Join-Path $c 'Install-OMPBridge.ps1')) { $sourceDir = $c; break }
}
if (-not $sourceDir) {
    throw "Fant ingen installasjons-katalog. Sjekket: $($sourceCandidates -join ', ')"
}

$bridgeSrc = Join-Path $sourceDir 'bridge\omp-bridge.mjs'
$version = (Select-String -Path $bridgeSrc -Pattern 'const VERSION = "([^"]+)"' |
    Select-Object -First 1).Matches.Groups[1].Value

Write-Host ""
Write-Host "NIMAY OMP BRIDGE - FULL INSTALLASJON" -ForegroundColor Cyan
Write-Host ("=" * 56) -ForegroundColor DarkGray
Write-Host "Kilde       : $sourceDir" -ForegroundColor DarkGray
Write-Host "Versjon     : $version" -ForegroundColor DarkGray
Write-Host ""

$results = [ordered]@{}

# --- Windows ------------------------------------------------------------------
if (-not $SkipWindows) {
    Write-Host ">>> Windows-installasjon (port $Port)" -ForegroundColor Cyan
    $installer = Join-Path $sourceDir 'Install-OMPBridge.ps1'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $installer -Port $Port
    $results['windows'] = $LASTEXITCODE -eq 0
    Write-Host ""
} else {
    Write-Host ">>> Windows hoppes over" -ForegroundColor DarkGray
    $results['windows'] = $true
}

# --- Linux --------------------------------------------------------------------
if (-not $SkipLinux) {
    Write-Host ">>> Linux-bygging og -installasjon (port $LinuxPort)" -ForegroundColor Cyan
    $linuxRoot = Join-Path $projectRoot 'linux-bridge'
    if (-not (Test-Path $linuxRoot)) {
        Write-Warn "Fant ikke linux-bridge i $linuxRoot - hopper over Linux."
        $results['linux'] = $false
    } else {
        $ok = $true
        if ($BuildLinuxPackages) {
            & (Join-Path $scriptDir 'Build-LinuxPackages.ps1') -Version $LinuxPort
            $ok = $LASTEXITCODE -eq 0
        }

        # Oppdater versjon i DEBIAN/control og rpm-spec, slik at pakken
        # matcher den konfigurerte porten/versjonen.
        $control = Join-Path $linuxRoot 'packaging\deb\DEBIAN\control'
        if (Test-Path $control) {
            $raw = Get-Content $control -Raw -Encoding UTF8
            [System.IO.File]::WriteAllText($control, ($raw -replace '(?m)^Version:.*', "Version: $LinuxPort"), (New-Object System.Text.UTF8Encoding($false)))
        }

        $deb = Join-Path $linuxRoot "nimay-omp-bridge_${LinuxPort}_amd64.deb"
        if (Test-Path $deb) {
            Write-Host "  Installerer .deb i WSL..." -ForegroundColor Cyan
            $wslDeb = (wsl.exe -e wslpath -a $deb).Trim()
            wsl.exe -u root -e sh -lc "dpkg -i '$wslDeb'" 2>&1 | ForEach-Object { "    $_" }
            if ($LASTEXITCODE -ne 0) { $ok = $false }
        } else {
            Write-Warning "Fant ingen .deb ($deb). Bruk -BuildLinuxPackages for å bygge den."
            $ok = $false
        }
        $results['linux'] = $ok
    }
    Write-Host ""
}

# --- Oppsummering --------------------------------------------------------------
Write-Host ("=" * 56) -ForegroundColor DarkGray
Write-Host "Windows : $(if ($results['windows']) { 'OK' } else { 'FEIL' })" -ForegroundColor $(if ($results['windows']) { 'Green' } else { 'Red' })
Write-Host "Linux   : $(if ($results['linux']) { 'OK' } else { 'FEIL' })" -ForegroundColor $(if ($results['linux']) { 'Green' } else { 'Red' })
Write-Host ""
Write-Host "Neste steg: .\Restart-Bridge.ps1 -Port $Port   (sømløs restart)" -ForegroundColor Cyan
Write-Host ""
