<#
.SYNOPSIS
    Importerer eksisterende bygde artefakter fra eldre mappestruktur inn i
    den versjonerte release-strukturen, uten å bygge noe på nytt.

.DESCRIPTION
    Flytter (ikke kopierer) de gamle APK-ene og Linux-pakkene inn under
    releases\<component>\<appName>-<version>\. Kildemappene beholdes, slik at
    ingenting går tapt, men release-mappen blir det autoritative stedet.

    Eksempel:
        .\Import-Existing.ps1
        .\Import-Existing.ps1 -Component android
#>
[CmdletBinding()]
param(
    [ValidateSet('bridge', 'android', 'linux', 'all')]
    [string]$Component = 'all'
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$manifest = Get-Content (Join-Path $scriptDir 'release-manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json

function Import-Apk {
    $src = 'C:\Prosjekter\Nimayomp'
    $apks = Get-ChildItem $src -Filter 'nimayomp-chatgpt-inspired-*.apk' -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime
    if (-not $apks) { Write-Host 'Ingen APK-er å importere.' -ForegroundColor Yellow; return }

    $cfg = $manifest.components.android
    foreach ($apk in $apks) {
        if ($apk.Name -match 'nimayomp-chatgpt-inspired-(?<v>\d+\.\d+\.\d+.*)\.apk$') {
            $version = $matches['v']
        } else { continue }

        $folder = Join-Path (Join-Path $manifest.releasesRoot 'android') ('{0}-{1}' -f $cfg.appName, $version)
        if (Test-Path $folder) { Write-Host "  Hopper over (finnes): $version" -ForegroundColor DarkGray; continue }

        New-Item -ItemType Directory -Path $folder -Force | Out-Null
        $dest = Join-Path $folder ('{0}-{1}.apk' -f $cfg.appName, $version)
        Copy-Item $apk.FullName $dest -Force
        Write-Host "  + android $version" -ForegroundColor Green
    }
}

function Import-Linux {
    $src = 'C:\Prosjekter\Nimayomp\linux-bridge'
    $cfg = $manifest.components.linux
    $pkgs = Get-ChildItem $src -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Extension -in '.deb', '.rpm' }
    if (-not $pkgs) { Write-Host 'Ingen Linux-pakker å importere.' -ForegroundColor Yellow; return }

    $versions = $pkgs | ForEach-Object {
        if ($_.Name -match '(?<v>\d+\.\d+\.\d+)') { $matches['v'] }
    } | Sort-Object -Unique

    foreach ($version in $versions) {
        $folder = Join-Path (Join-Path $manifest.releasesRoot 'linux') ('{0}-{1}' -f $cfg.appName, $version)
        if (Test-Path $folder) { Write-Host "  Hopper over (finnes): $version" -ForegroundColor DarkGray; continue }

        New-Item -ItemType Directory -Path $folder -Force | Out-Null
        foreach ($p in $pkgs | Where-Object { $_.Name -match [regex]::Escape($version) }) {
            Copy-Item $p.FullName (Join-Path $folder $p.Name) -Force
        }
        foreach ($extra in @('nimay-omp-bridge.container', 'install.sh', 'uninstall.sh', 'Containerfile', 'README.md')) {
            $p = Join-Path $src $extra
            if (Test-Path $p) { Copy-Item $p (Join-Path $folder $extra) -Force }
        }
        Write-Host "  + linux $version" -ForegroundColor Green
    }
}

function Import-Bridge {
    $cfg = $manifest.components.bridge
    $bridgeDir = 'C:\Prosjekter\Nimayworkingbridgeinstaller\bridge'
    $m = [regex]::Match((Get-Content (Join-Path $bridgeDir 'omp-bridge.mjs') -Raw -Encoding UTF8), 'const VERSION = "([^"]+)"')
    if (-not $m.Success) { throw 'Fant ikke VERSION i broen.' }
    $version = $m.Groups[1].Value

    $folder = Join-Path (Join-Path $manifest.releasesRoot 'bridge') ('{0}-{1}' -f $cfg.appName, $version)
    if (Test-Path $folder) { Write-Host "  Hopper over (finnes): $version" -ForegroundColor DarkGray; return }

    New-Item -ItemType Directory -Path $folder -Force | Out-Null
    Copy-Item (Join-Path $bridgeDir 'omp-bridge.mjs') (Join-Path $folder 'omp-bridge.mjs') -Force
    if (Test-Path (Join-Path $bridgeDir 'package.json')) {
        Copy-Item (Join-Path $bridgeDir 'package.json') (Join-Path $folder 'package.json') -Force
    }
    foreach ($d in @('tools', 'commands')) {
        $src = Join-Path 'C:\Prosjekter\Nimayworkingbridgeinstaller' $d
        if (Test-Path $src) {
            # node_modules bygges ved installasjon og skal ikke inngå i en release.
            Get-ChildItem $src -Recurse -Force | Where-Object {
                $_.FullName -notmatch '[\\/]node_modules([\\/]|$)'
            } | ForEach-Object {
                $rel = $_.FullName.Substring($src.Length).TrimStart('\', '/')
                $target = Join-Path (Join-Path $folder $d) $rel
                if ($_.PSIsContainer) {
                    New-Item -ItemType Directory -Path $target -Force | Out-Null
                } else {
                    New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
                    Copy-Item $_.FullName $target -Force
                }
            }
        }
    }
    Write-Host "  + bridge $version" -ForegroundColor Green
}

Write-Host "Importerer eksisterende artefakter..." -ForegroundColor Cyan
if ($Component -in @('android', 'all')) { Import-Apk }
if ($Component -in @('linux', 'all')) { Import-Linux }
if ($Component -in @('bridge', 'all')) { Import-Bridge }
Write-Host "Ferdig." -ForegroundColor Green
