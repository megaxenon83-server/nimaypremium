<#
.SYNOPSIS
    Øker versjonsnummeret i kilden for én komponent, og publiserer en release.

.DESCRIPTION
    Starter på patch-nivå (x.y.z -> x.y.(z+1)), men kan overstyres med
    -Version for eksplisitt versjon. Versjonen skrives tilbake til
    kildefilen, slik at kilde og release-mappe aldri er uenige.

    Eksempel:
        .\Bump-And-Release.ps1 -Component bridge
        .\Bump-And-Release.ps1 -Component android -BuildApk
        .\Bump-And-Release.ps1 -Component linux -BuildLinuxPackages
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('bridge', 'android', 'linux')]
    [string]$Component,

    [string]$Version,

    [string]$Notes = '',

    [switch]$BuildApk,
    [switch]$BuildLinuxPackages,

    # Bare vis hva som ville blitt endret.
    [switch]$WhatIfOnly
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$manifest = Get-Content (Join-Path $scriptDir 'release-manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$cfg = $manifest.components.$Component

# --- Les gjeldende versjon ----------------------------------------------------
$src = $cfg.versionSource
switch ($src.kind) {
    'regex' {
        $raw = Get-Content $src.file -Raw -Encoding UTF8
        $m = [regex]::Match($raw, $src.pattern)
        if (-not $m.Success) { throw "Fant ikke versjonsmønster i $($src.file)" }
        $current = $m.Groups[1].Value.Trim()
    }
    'gradle' {
        $raw = Get-Content $src.file -Raw -Encoding UTF8
        $idx = $raw.IndexOf($src.flavor)
        if ($idx -lt 0) { throw "Fant ikke flavor '$($src.flavor)'" }
        $block = $raw.Substring($idx, [Math]::Min(2000, $raw.Length - $idx))
        $current = [regex]::Match($block, 'versionName\s+"([^"]+)"').Groups[1].Value
    }
    default { throw "Ukjent kind '$($src.kind)'" }
}

# --- Bestem ny versjon --------------------------------------------------------
if ($Version) {
    $new = $Version
} else {
    # Øk bare patch-delen, og behold eventuell suffix (f.eks. -chatgpt-inspired).
    if ($current -match '^(?<num>\d+\.\d+\.\d+)(?<suffix>.*)$') {
        $parts = $matches['num'].Split('.')
        $new = '{0}.{1}.{2}{3}' -f $parts[0], $parts[1], ([int]$parts[2] + 1), $matches['suffix']
    } elseif ($current -match '^(?<num>\d+\.\d+)(?<suffix>.*)$') {
        $parts = $matches['num'].Split('.')
        $new = '{0}.{1}.0{2}' -f $parts[0], ([int]$parts[1] + 1), $matches['suffix']
    } else {
        throw "Kan ikke tolke versjon '$current' for automatisk bump. Bruk -Version."
    }
}

Write-Host "Komponent : $($cfg.label)" -ForegroundColor Cyan
Write-Host "Nåværende: $current" -ForegroundColor Cyan
Write-Host "Ny       : $new" -ForegroundColor Cyan

if ($new -eq $current) {
    throw "Ny versjon er lik den gamle. Angi -Version for å velge en annen."
}

if ($WhatIfOnly) {
    Write-Host "`nDry-run. Ingen filer endret." -ForegroundColor Yellow
    return
}

# --- Skriv ny versjon til kilden ----------------------------------------------
switch ($src.kind) {
    'regex' {
        $raw = Get-Content $src.file -Raw -Encoding UTF8
        $updated = [regex]::Replace($raw, $src.pattern, { param($m) $m.Value -replace $m.Groups[1].Value, $new }, 1)
        [System.IO.File]::WriteAllText($src.file, $updated, (New-Object System.Text.UTF8Encoding($false)))
    }
    'gradle' {
        $raw = Get-Content $src.file -Raw -Encoding UTF8
        $idx = $raw.IndexOf($src.flavor)
        if ($idx -lt 0) { throw "Fant ikke flavor '$($src.flavor)'" }
        $head = $raw.Substring(0, $idx)
        $tail = $raw.Substring($idx)
        $tail = [regex]::Replace($tail, 'versionName\s+"[^"]+"', { param($m) $m.Value -replace '"[^"]+"', "`"$new`"" }, 1)
        # Bare bump versionCode hvis den finnes i flavor-blokken.
        if ($tail -match 'versionCode\s+\d+' -and $tail -match 'versionCode\s+\d+' -and
            ($tail.Substring(0, [Math]::Min(2000, $tail.Length)) -match 'versionCode\s+\d+')) {
            $tail = [regex]::Replace($tail, 'versionCode\s+(\d+)', {
                    param($m)
                    $n = [int]$m.Groups[1].Value
                    if ($m.Index -lt [Math]::Min(2000, $tail.Length)) { "versionCode $($n + 1)" } else { $m.Value }
                }, 1)
        }
        [System.IO.File]::WriteAllText($src.file, $head + $tail, (New-Object System.Text.UTF8Encoding($false)))
    }
}

Write-Host "Oppdatert: $($src.file)" -ForegroundColor Green

# --- Publiser ------------------------------------------------------------------
$releaseArgs = @{ Component = $Component; Version = $new; Notes = $Notes }
if ($BuildApk) { $releaseArgs['BuildApk'] = $true }
if ($BuildLinuxPackages) { $releaseArgs['BuildLinuxPackages'] = $true }

& (Join-Path $scriptDir 'New-Release.ps1') @releaseArgs
