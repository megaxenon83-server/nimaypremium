<#
.SYNOPSIS
    Hovedkommando for Nimay OMP releases.

.DESCRIPTION
    En tydelig inngang til release-verktøyene. Uten parametre viser den
    hva som finnes og hvilke kommandoer som er tilgjengelige.

    Eksempel:
        .\omp-release.ps1                       # vis status
        .\omp-release.ps1 -Status               # bare status
        .\omp-release.ps1 -New bridge           # ny patch for broen
        .\omp-release.ps1 -New android -Build   # bygg APK + ny patch
        .\omp-release.ps1 -New linux -Build     # bygg pakker + ny patch
        .\omp-release.ps1 -Publish bridge -Version 2.9.26
#>
[CmdletBinding()]
param(
    # Komponent å lage ny release for.
    [ValidateSet('bridge', 'android', 'linux')]
    [string]$New,

    # Publiser med eksplisitt versjon (uten å bumpe kilden).
    [ValidateSet('bridge', 'android', 'linux')]
    [string]$Publish,

    [string]$Version,
    [string]$Notes = '',

    # Bygg artefakter før publisering.
    [switch]$Build,

    # Vis bare status/oversikt.
    [switch]$Status
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition

function Show-Status {
    $manifest = Get-Content (Join-Path $scriptDir 'release-manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    Write-Host ""
    Write-Host "NIMAY OMP - RELEASES" -ForegroundColor Cyan
    Write-Host ("=" * 60) -ForegroundColor DarkGray
    Write-Host "Rot: $($manifest.releasesRoot)" -ForegroundColor DarkGray
    Write-Host ""

    foreach ($name in $manifest.components.PSObject.Properties.Name) {
        $cfg = $manifest.components.$name
        $dir = Join-Path $manifest.releasesRoot $name
        Write-Host ("{0} ({1})" -f $cfg.label, $name) -ForegroundColor Cyan
        if (-not (Test-Path $dir)) { Write-Host "   (ingen releases)" -ForegroundColor DarkGray; continue }
        foreach ($d in (Get-ChildItem $dir -Directory | Sort-Object Name)) {
            $files = Get-ChildItem $d.FullName -File | Where-Object { $_.Name -ne 'release.json' }
            $kb = [math]::Round((($files | Measure-Object Length -Sum).Sum) / 1KB, 1)
            Write-Host ("   {0,-46} {1,3} filer {2,8} KB" -f $d.Name, $files.Count, $kb) -ForegroundColor Gray
        }
        Write-Host ""
    }
}

Show-Status

if ($Status) { return }

if ($New) {
    $args = @{ Component = $New }
    if ($Version) { $args['Version'] = $Version }
    if ($Notes) { $args['Notes'] = $Notes }
    if ($Build) {
        if ($New -eq 'android') { $args['BuildApk'] = $true }
        if ($New -eq 'linux') { $args['BuildLinuxPackages'] = $true }
    }
    & (Join-Path $scriptDir 'Bump-And-Release.ps1') @args
    return
}

if ($Publish) {
    $args = @{ Component = $Publish }
    if ($Version) { $args['Version'] = $Version }
    if ($Notes) { $args['Notes'] = $Notes }
    & (Join-Path $scriptDir 'New-Release.ps1') @args
    return
}

Write-Host "Kommandoer:" -ForegroundColor Yellow
Write-Host "  .\omp-release.ps1 -New bridge           Ny patch for broen (bumper 2.9.x)"
Write-Host "  .\omp-release.ps1 -New android -Build   Bygg APK + ny patch"
Write-Host "  .\omp-release.ps1 -New linux -Build     Bygg .deb/.rpm + ny patch"
Write-Host "  .\omp-release.ps1 -Publish bridge -Version 2.9.26"
Write-Host ""
