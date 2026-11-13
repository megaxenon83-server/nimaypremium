<#
.SYNOPSIS
    Bygger releases\index.json fra mappene på disk.

.DESCRIPTION
    Indexen speiler alltid hvilke release-mapper som faktisk finnes, så den
    aldri kommer i utakt med virkeligheten etter sletting eller flytting.

    Eksempel:
        .\Update-Index.ps1
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$manifest = Get-Content (Join-Path $scriptDir 'release-manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json

$index = [ordered]@{
    updatedAt = (Get-Date).ToUniversalTime().ToString('o')
    releases  = @()
}

foreach ($componentName in $manifest.components.PSObject.Properties.Name) {
    $compCfg = $manifest.components.$componentName
    $compDir = Join-Path $manifest.releasesRoot $componentName
    if (-not (Test-Path $compDir)) { continue }

    foreach ($d in (Get-ChildItem $compDir -Directory -ErrorAction SilentlyContinue)) {
        $ver = $d.Name -replace ('^{0}-' -f [regex]::Escape($compCfg.appName)), ''
        $createdAt = $d.CreationTime.ToUniversalTime().ToString('o')
        $notes = ''

        $metaPath = Join-Path $d.FullName 'release.json'
        if (Test-Path $metaPath) {
            try {
                $meta = Get-Content $metaPath -Raw -Encoding UTF8 | ConvertFrom-Json
                if ($meta.createdAt) { $createdAt = $meta.createdAt }
                if ($meta.notes) { $notes = $meta.notes }
            } catch { }
        }

        $index.releases += [ordered]@{
            component = $componentName
            appName   = $compCfg.appName
            version   = $ver
            folder    = "{0}\{1}" -f $componentName, $d.Name
            createdAt = $createdAt
            notes     = $notes
        }
    }
}

$index.releases = @($index.releases | Sort-Object `
    @{ Expression = { "$($_.component)" } }, `
    @{ Expression = { [version]($_.version -replace '-.*$', '') } } -Descending)

$indexPath = Join-Path $manifest.releasesRoot 'index.json'
New-Item -ItemType Directory -Path (Split-Path -Parent $indexPath) -Force | Out-Null
$index | ConvertTo-Json -Depth 6 | Set-Content $indexPath -Encoding UTF8

Write-Host "Oppdatert index med $($index.releases.Count) releases: $indexPath" -ForegroundColor Green
