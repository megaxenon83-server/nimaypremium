<#
.SYNOPSIS
    Publiserer en ny versjonert release-mappe for Nimay OMP.

.DESCRIPTION
    Oppretter en ny mappe per patch under releases\<component>\<appName>-<version>\.
    Artifactene kopieres inn i den nye mappen, slik at hver patch får en
    egen, uendret mappe. Ingen eksisterende release-mapper røres.

    Eksempel:
        .\New-Release.ps1 -Component bridge
        .\New-Release.ps1 -Component android -Version 3.0.43
        .\New-Release.ps1 -Component linux -Version 1.0.1

    Hvis -Version ikke oppgis, leses versjonen fra release-manifest.json.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('bridge', 'android', 'linux')]
    [string]$Component,

    [string]$Version,

    [string]$Notes = '',

    # Bygg APK-en før publisering (krever Android SDK + JDK).
    [switch]$BuildApk,

    # Bygg .deb/.rpm før publisering (krever WSL med dpkg-deb/rpmbuild).
    [switch]$BuildLinuxPackages,

    # Ikke kopier noe; bare vis hva som ville blitt gjort.
    [switch]$WhatIfOnly
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$manifestPath = Join-Path $scriptDir 'release-manifest.json'

if (-not (Test-Path $manifestPath)) {
    throw "Fant ikke release-manifest.json i $scriptDir"
}

$manifest = Get-Content $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
$cfg = $manifest.components.$Component
if (-not $cfg) { throw "Ukjent komponent '$Component'" }

# --- Finn versjonen -----------------------------------------------------------
function Get-VersionFromSource {
    param($Source)
    switch ($Source.kind) {
        'regex' {
            if (-not (Test-Path $Source.file)) {
                throw "Kildefil for versjon mangler: $($Source.file)"
            }
            $raw = Get-Content $Source.file -Raw -Encoding UTF8
            $m = [regex]::Match($raw, $Source.pattern)
            if (-not $m.Success) {
                throw "Fant ikke versjonsmønster '$($Source.pattern)' i $($Source.file)"
            }
            return $m.Groups[1].Value.Trim()
        }
        'gradle' {
            if (-not (Test-Path $Source.file)) {
                throw "build.gradle mangler: $($Source.file)"
            }
            $raw = Get-Content $Source.file -Raw -Encoding UTF8
            # Finn den aktuelle flavor-blokken og les versionName/versionCode derfra.
            $flavorIdx = $raw.IndexOf($Source.flavor)
            if ($flavorIdx -lt 0) { throw "Fant ikke flavor '$($Source.flavor)' i $($Source.file)" }
            $block = $raw.Substring($flavorIdx, [Math]::Min(2000, $raw.Length - $flavorIdx))
            $name = [regex]::Match($block, 'versionName\s+"([^"]+)"')
            $code = [regex]::Match($block, 'versionCode\s+(\d+)')
            if (-not $name.Success) { throw "Fant ikke versionName for flavor '$($Source.flavor)'" }
            $result = @{ name = $name.Groups[1].Value; code = 0 }
            if ($code.Success) { $result.code = [int]$code.Groups[1].Value }
            return $result
        }
        default { throw "Ukjent kind '$($Source.kind)'" }
    }
}

$sourceInfo = Get-VersionFromSource -Source $cfg.versionSource
$detectedVersion = if ($sourceInfo -is [hashtable]) { $sourceInfo.name } else { $sourceInfo }
$versionCode = if ($sourceInfo -is [hashtable]) { $sourceInfo.code } else { 0 }

if (-not $Version) { $Version = $detectedVersion }
if ($Version -ne $detectedVersion) {
    Write-Warning "Oppgitt versjon '$Version' avviker fra kilden '$detectedVersion'. Kilden brukes ikke til å bygge, kun til å verifisere."
}

# versionName i build.gradle bærer allerede suffikset (f.eks. "3.0.42-chatgpt-inspired").
# Stripp suffikset her, slik at mappenavnet blir "app-3.0.42" og ikke dobbelt.
$folderVersion = $Version -replace '-chatgpt-inspired$', ''

if ($Version -notmatch '^\d+\.\d+(\.\d+)?([-.][0-9A-Za-z.-]+)?$') {
    throw "Ugyldig versjon '$Version'. Forventet format 1.2.3 eller 3.0.43-chatgpt-inspired."
}

# --- Bygg (valgfritt) ---------------------------------------------------------
$artifactRoot = $cfg.artifactSource

if ($BuildApk) {
    if ($Component -ne 'android') { throw '-BuildApk gjelder bare android.' }
    $appRoot = $artifactRoot
    Write-Host "Bygger APK..." -ForegroundColor Cyan
    Push-Location $appRoot
    try {
        $gradlew = if (Test-Path '.\gradlew.bat') { '.\gradlew.bat' } else { 'gradlew' }
        & $gradlew :app:assembleChstgptRelease
        if ($LASTEXITCODE -ne 0) { throw "Gradle-bygging feilet ($LASTEXITCODE)" }
    } finally { Pop-Location }
}

if ($BuildLinuxPackages) {
    if ($Component -ne 'linux') { throw '-BuildLinuxPackages gjelder bare linux.' }
    Write-Host "Bygger .deb og .rpm via WSL..." -ForegroundColor Cyan

    $debPackaging = Join-Path $artifactRoot 'packaging\deb'
    $debRoot = Join-Path $debPackaging 'deb-root'
    $debControl = Join-Path $debPackaging 'DEBIAN'

    # Byggingen skjer i en Linux-native mappe i WSL. Filer mountet fra /mnt/c
    # kan ikke få korrekte rettigheter (dpkg-deb avviser 777), og dpkg-deb
    # krever DEBIAN/control INNE i deb-root. Derfor kopieres deb-root +
    # DEBIAN til /tmp, bygges der, og resultatet kopieres tilbake.
    $buildDir = "/tmp/nimay-omp-build-$folderVersion"
    wsl.exe -e sh -lc "rm -rf '$buildDir' && mkdir -p '$buildDir'" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Kunne ikke opprette byggemappe i WSL.' }

    $wslRoot = (wsl.exe -e wslpath -a $artifactRoot).Trim()
    $debName = "nimay-omp-bridge_${folderVersion}_amd64.deb"
    $debBuilt = (wsl.exe -e wslpath -a (Join-Path $artifactRoot $debName)).Trim()

    try {
        wsl.exe -e sh -lc "cp -r '$wslRoot/packaging/deb/deb-root' '$buildDir/deb-root' && cp -r '$wslRoot/packaging/deb/DEBIAN' '$buildDir/deb-root/DEBIAN' && chmod -R u+rwX,go+rX '$buildDir/deb-root' && chmod 0755 '$buildDir/deb-root/DEBIAN' && chmod 0644 '$buildDir/deb-root/DEBIAN/control' && chmod 0755 '$buildDir/deb-root/DEBIAN'/postinst '$buildDir/deb-root/DEBIAN'/prerm '$buildDir/deb-root/DEBIAN'/postrm 2>/dev/null; true" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Kunne ikke klargjøre deb-root i WSL.' }

        wsl.exe -e sh -lc "cd '$buildDir' && dpkg-deb --build deb-root '$debName'" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "dpkg-deb feilet ($LASTEXITCODE)" }

        # Kopier .deb tilbake til Windows-kilden.
        wsl.exe -e sh -lc "cp '$buildDir/$debName' '$debBuilt'" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Kunne ikke kopiere .deb tilbake.' }

        $rpmTop = (wsl.exe -e wslpath -a (Join-Path $artifactRoot 'packaging\rpm')).Trim()
        # _topdir må være en Linux-sti. HOME i WSL kan peke på en Windows-mappe,
        # som rpmbuild ikke kan bruke som byggekatalog.
        $rpmTopdir = "/tmp/nimay-omp-rpm-$folderVersion"
        # Spec-en forventer en kilde-tarball med bridge/, scripts/, tools/,
        # voice/ og servicefilen i rot. Den settes sammen her.
        $tarName = "nimay-omp-bridge-${folderVersion}.tar.gz"
        wsl.exe -e sh -lc "rm -rf '$rpmTopdir' && mkdir -p '$rpmTopdir/SOURCES' '$buildDir/src/nimay-omp-bridge-$folderVersion' && cp -r '$wslRoot/payload/bridge' '$buildDir/src/nimay-omp-bridge-$folderVersion/bridge' && cp -r '$wslRoot/scripts' '$buildDir/src/nimay-omp-bridge-$folderVersion/scripts' && cp -r '$wslRoot/payload/tools' '$buildDir/src/nimay-omp-bridge-$folderVersion/tools' && cp -r '$wslRoot/payload/voice' '$buildDir/src/nimay-omp-bridge-$folderVersion/voice' && cp '$wslRoot/payload/nimay-omp-bridge.service' '$buildDir/src/nimay-omp-bridge-$folderVersion/nimay-omp-bridge.service' && tar -czf '$rpmTopdir/SOURCES/$tarName' -C '$buildDir/src' 'nimay-omp-bridge-$folderVersion'" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Kunne ikke lage RPM-kildetarball.' }

        # Oppdater spec-en så den peker på den nye versjonen og tarballen.
        $specPath = Join-Path $artifactRoot 'packaging\rpm\nimay-omp-bridge.spec'
        $specRaw = Get-Content $specPath -Raw -Encoding UTF8
        $specNew = [regex]::Replace($specRaw, '(?m)^Version:\s*\S+', "Version:        $folderVersion")
        $specNew = [regex]::Replace($specNew, '(?m)^Release:\s*\S+', 'Release:        1%{?dist}')
        [System.IO.File]::WriteAllText($specPath, $specNew, (New-Object System.Text.UTF8Encoding($false)))

        wsl.exe -e sh -lc "cd '$rpmTop' && rpmbuild -bb --define '_topdir $rpmTopdir' nimay-omp-bridge.spec" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "rpmbuild feilet ($LASTEXITCODE)" }
        # Hent ferdigbygde .rpm ut av topdir og legg dem i kilden.
        wsl.exe -e sh -lc "find '$rpmTopdir' -name '*.noarch.rpm' -exec cp {} '$rpmTop' \;" | Out-Null
        wsl.exe -e sh -lc "rm -rf '$rpmTopdir'" | Out-Null

        # rpmbuild legger RPM-en i packaging/rpm; flytt den til roten av
        # linux-bridge, slik at den ligger samme sted som .deb-pakken.
        $rpmBuilt = Join-Path $artifactRoot "nimay-omp-bridge-$folderVersion-1.noarch.rpm"
        $rpmFrom = Join-Path (Join-Path $artifactRoot 'packaging\rpm') "nimay-omp-bridge-$folderVersion-1.noarch.rpm"
        if (Test-Path $rpmFrom) { Move-Item $rpmFrom $rpmBuilt -Force }
    } finally {
        wsl.exe -e sh -lc "rm -rf '$buildDir'" | Out-Null
    }
}

# --- Målmappe -----------------------------------------------------------------
$releaseDir = Join-Path $manifest.releasesRoot $Component
$folderName = '{0}-{1}' -f $cfg.appName, $folderVersion
$targetDir = Join-Path $releaseDir $folderName

if (Test-Path $targetDir) {
    # I dry-run er dette bare informasjon; ved ekte publisering er det en feil.
    if (-not $WhatIfOnly) {
        throw "Release-mappen finnes allerede: $targetDir`nBruk en ny versjon, eller slett mappen manuelt hvis den skal bygges på nytt."
    }
    Write-Warning "Release-mappen finnes allerede: $targetDir (ville blitt overskrevet)"
}

Write-Host ""
Write-Host "Komponent : $($cfg.label) ($Component)" -ForegroundColor Cyan
Write-Host "Versjon  : $Version" -ForegroundColor Cyan
Write-Host "Mål      : $targetDir" -ForegroundColor Cyan
Write-Host ""

if ($WhatIfOnly) {
    Write-Host "--- Planlagte artefakter (dry-run) ---" -ForegroundColor Yellow
    foreach ($a in $cfg.artifacts) {
        $fromRel = $a.from.Replace('{appName}', $cfg.appName).Replace('{version}', $folderVersion)
        $from = Join-Path $artifactRoot $fromRel
        $exists = Test-Path $from
        $name = if ($a.renameTo) { $a.renameTo.Replace('{appName}', $cfg.appName).Replace('{version}', $folderVersion) } else { $a.name }
        $flag = if ($exists) { 'OK ' } elseif ($a.optional) { 'valgfri' } else { 'MANGLER' }
        Write-Host ("  [{0}] {1}  <- {2}" -f $flag, $name, $fromRel)
    }
    Write-Host ""
    Write-Host "Dry-run. Ingen filer skrevet." -ForegroundColor Yellow
    return
}

# --- Kopier artefakter --------------------------------------------------------
New-Item -ItemType Directory -Path $targetDir -Force | Out-Null

$copied = @()
$missing = @()
foreach ($a in $cfg.artifacts) {
    $fromRel = $a.from.Replace('{appName}', $cfg.appName).Replace('{version}', $folderVersion)
    $from = Join-Path $artifactRoot $fromRel
    if (-not (Test-Path $from)) {
        if ($a.optional) { $missing += $a.name; continue }
        throw "Artefakt mangler: $from"
    }
    $name = if ($a.renameTo) { $a.renameTo.Replace('{appName}', $cfg.appName).Replace('{version}', $folderVersion) } else { $a.name }
    $dest = Join-Path $targetDir $name

    if ($a.redact) {
        # Skriv ut en konfigurasjonsmal uten hemmeligheter, slik at en release
        # aldri inneholder et gyldig token.
        $obj = Get-Content $from -Raw -Encoding UTF8 | ConvertFrom-Json
        foreach ($prop in $a.redact.PSObject.Properties) {
            if ($obj.PSObject.Properties.Name -contains $prop.Name) {
                $obj.$($prop.Name) = $prop.Value
            }
        }
        $obj | ConvertTo-Json -Depth 10 | Set-Content $dest -Encoding UTF8
    } elseif ($a.recursive) {
        # node_modules bygges ved installasjon og skal ikke inngå i en release.
        Get-ChildItem $from -Recurse -Force | Where-Object {
            $_.FullName -notmatch '[\\/]node_modules([\\/]|$)'
        } | ForEach-Object {
            $rel = $_.FullName.Substring($from.Length).TrimStart('\', '/')
            $target = Join-Path $dest $rel
            if ($_.PSIsContainer) {
                New-Item -ItemType Directory -Path $target -Force | Out-Null
            } else {
                New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
                Copy-Item $_.FullName $target -Force
            }
        }
    } else {
        Copy-Item $from $dest -Force
    }
    $copied += $name
    Write-Host "  + $name" -ForegroundColor Green
}

# --- release.json -------------------------------------------------------------
$releaseInfo = [ordered]@{
    component   = $Component
    label       = $cfg.label
    appName     = $cfg.appName
    version     = $folderVersion
    versionName = $Version
    versionCode = $versionCode
    createdAt   = (Get-Date).ToUniversalTime().ToString('o')
    notes       = $Notes
    artifacts   = @()
}

foreach ($name in $copied) {
    $p = Join-Path $targetDir $name
    $entry = [ordered]@{ name = $name }
    if (Test-Path $p -PathType Leaf) {
        $item = Get-Item $p
        $entry.size = $item.Length
        $entry.sha256 = (Get-FileHash $p -Algorithm SHA256).Hash
    } elseif (Test-Path $p -PathType Container) {
        $files = Get-ChildItem $p -Recurse -File
        $entry.size = ($files | Measure-Object Length -Sum).Sum
        $entry.files = $files.Count
        # En samlet hash over alle filer gjør at mapper kan sammenlignes.
        $lines = $files | Sort-Object FullName | ForEach-Object {
            '{0}  {1}' -f (Get-FileHash $_.FullName -Algorithm SHA256).Hash, $_.FullName.Substring($p.Length)
        }
        $entry.sha256 = [System.BitConverter]::ToString(
            [System.Security.Cryptography.SHA256]::Create().ComputeHash(
                [System.Text.Encoding]::UTF8.GetBytes(($lines -join "`n"))
            )
        ).Replace('-', '')
    }
    $releaseInfo.artifacts += $entry
}

$releaseInfo | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $targetDir 'release.json') -Encoding UTF8

# --- Oppdater index -----------------------------------------------------------
# Indexen bygges fra mappene på disk, slik at den alltid er konsistent
# med hva som faktisk finnes, også etter sletting eller manuell flytting.
$indexPath = Join-Path $manifest.releasesRoot 'index.json'
$index = [ordered]@{ updatedAt = (Get-Date).ToUniversalTime().ToString('o'); releases = @() }

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
$index.releases = @($index.releases | Sort-Object @{ Expression = { "$($_.component)" } }, @{ Expression = { [version]($_.version -replace '-.*$', '') } } -Descending)
New-Item -ItemType Directory -Path (Split-Path -Parent $indexPath) -Force | Out-Null
$index | ConvertTo-Json -Depth 6 | Set-Content $indexPath -Encoding UTF8

Write-Host ""
Write-Host "Release ferdig: $targetDir" -ForegroundColor Green
if ($missing.Count -gt 0) {
    Write-Host "Hoppet over (valgfrie, manglet): $($missing -join ', ')" -ForegroundColor Yellow
}
