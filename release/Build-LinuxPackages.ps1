<#
.SYNOPSIS
    Bygger .deb og .rpm for Nimay OMP Bridge i WSL.

.DESCRIPTION
    Byggingen skjer i en Linux-native mappe i WSL (/tmp), fordi filer
    mountet fra /mnt/c ikke kan fa korrekte rettigheter - dpkg-deb avviser
    777-rettigheter. Kildetarballen for RPM-en settes sammen her, siden
    spec-en forventer den.

    Eksempel:
        .\Build-LinuxPackages.ps1 -Version 1.0.0
#>
[CmdletBinding()]
param(
    [string]$Version = '1.0.0'
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$projectRoot = Split-Path -Parent $scriptDir
$linuxRoot = Join-Path $projectRoot 'linux-bridge'

if (-not (Test-Path $linuxRoot)) {
    throw "Fant ikke linux-bridge i $linuxRoot"
}

Write-Host "Bygger Linux-pakker (versjon $Version)..." -ForegroundColor Cyan

$buildDir = "/tmp/nimay-omp-build-$Version"
$rpmTopdir = "/tmp/nimay-omp-rpm-$Version"
$wslRoot = (wsl.exe -e wslpath -a $linuxRoot).Trim()
$debName = "nimay-omp-bridge_${Version}_amd64.deb"
$tarName = "nimay-omp-bridge-${Version}.tar.gz"
$debBuilt = (wsl.exe -e wslpath -a (Join-Path $linuxRoot $debName)).Trim()

try {
    # Kildene pakkes pa nytt hver gang, slik at endringer i scripts/
    # (configure.sh, write-omp-bin.sh) alltid kommer med i pakken.
    $debScripts = Join-Path $linuxRoot 'packaging\deb\deb-root\opt\nimay-omp-bridge\scripts'
    $payloadScripts = Join-Path $linuxRoot 'payload\nimay-omp-bridge-1.0.0\scripts'
    foreach ($d in @($debScripts, $payloadScripts)) {
        if (-not (Test-Path $d)) { New-Item -ItemType Directory -Path $d -Force | Out-Null }
        Copy-Item (Join-Path $linuxRoot 'scripts\configure.sh') $d -Force
        Copy-Item (Join-Path $linuxRoot 'scripts\write-omp-bin.sh') $d -Force
    }

    # Linux-filer maa ha LF-linjeslutt. Kildene ligger pa Windows og har
    # derfor CRLF, som gir rpmbuild-feilen "not found" i autosetup og
    # ugyldige shebang-linjer i skripter.
    Write-Host "Normaliserer linjeslutt til LF ..." -ForegroundColor DarkGray
    $lfFix = @(
        (Join-Path $linuxRoot 'scripts\configure.sh'),
        (Join-Path $linuxRoot 'scripts\write-omp-bin.sh'),
        (Join-Path $linuxRoot 'install.sh'),
        (Join-Path $linuxRoot 'uninstall.sh'),
        (Join-Path $linuxRoot 'packaging\deb\DEBIAN\control'),
        (Join-Path $linuxRoot 'packaging\deb\DEBIAN\postinst'),
        (Join-Path $linuxRoot 'packaging\deb\DEBIAN\prerm'),
        (Join-Path $linuxRoot 'packaging\deb\DEBIAN\postrm'),
        (Join-Path $linuxRoot 'packaging\rpm\nimay-omp-bridge.spec')
    )
    foreach ($f in $lfFix) {
        if (Test-Path $f) {
            $t = [System.IO.File]::ReadAllText($f)
            [System.IO.File]::WriteAllText($f, ($t -replace "`r`n", "`n"), (New-Object System.Text.UTF8Encoding($false)))
        }
    }
    # .mjs/.py/.ps1 i payloaden maa ogsa ha LF.
    foreach ($dir in @(
        (Join-Path $linuxRoot 'payload\bridge'),
        (Join-Path $linuxRoot 'payload\tools'),
        (Join-Path $linuxRoot 'payload\voice'),
        (Join-Path $linuxRoot 'payload\nimay-omp-bridge-1.0.0')
    )) {
        if (-not (Test-Path $dir)) { continue }
        Get-ChildItem $dir -Recurse -File -Force | Where-Object {
            $_.Extension -in '.mjs', '.py', '.sh', '.service', '.json', '.cmd', '.ps1' -and
            $_.FullName -notmatch 'node_modules'
        } | ForEach-Object {
            $t = [System.IO.File]::ReadAllText($_.FullName)
            if ($t.Contains("`r`n")) {
                [System.IO.File]::WriteAllText($_.FullName, ($t -replace "`r`n", "`n"), (New-Object System.Text.UTF8Encoding($false)))
            }
        }
    }

    # --- .deb ------------------------------------------------------------------
    wsl.exe -e sh -lc "rm -rf '$buildDir' && mkdir -p '$buildDir'" | Out-Null
    # DEBIAN-katalogen kopieres fra kilden, og rettighetene settes eksplisitt:
    # dpkg-deb avviser 777-rettigheter, og maintainer-scripts maa vaere 0755.
    # Kommandoene lenker med '|| true' slik at en manglende fil ikke
    # avbryter byggingen.
    wsl.exe -e sh -lc "cp -r '$wslRoot/packaging/deb/deb-root' '$buildDir/deb-root' && cp -r '$wslRoot/packaging/deb/DEBIAN' '$buildDir/deb-root/DEBIAN' && chmod -R u+rwX,go+rX '$buildDir/deb-root' && chmod 0755 '$buildDir/deb-root/DEBIAN' && chmod 0644 '$buildDir/deb-root/DEBIAN/control' && chmod 0755 '$buildDir/deb-root/DEBIAN'/postinst '$buildDir/deb-root/DEBIAN'/prerm '$buildDir/deb-root/DEBIAN'/postrm 2>/dev/null; true" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Kunne ikke klargjøre deb-root i WSL.' }

    wsl.exe -e sh -lc "cd '$buildDir' && dpkg-deb --build --root-owner-group deb-root '$debName'" 2>&1 |
        Where-Object { $_ -notmatch 'warning|hint:|unusual owner' } | ForEach-Object { "  $_" }
    # wsl.exe returnerer non-zero naar dpkg-deb skriver advarsler til stderr,
    # saa pakken verifiseres pa fil istedenfor pa exit-koden.
    wsl.exe -e sh -lc "cp '$buildDir/$debName' '$debBuilt'" | Out-Null
    if (-not (Test-Path (Join-Path $linuxRoot $debName))) { throw "dpkg-deb feilet (ingen .deb ble laget)" }
    Write-Host "  + $debName" -ForegroundColor Green

    # --- .rpm ------------------------------------------------------------------
    # Kildetarballen for RPM-en bestaar av bridge/, scripts/, tools/, voice/
    # og servicefilen i rot. Den bygges fra de samme kildene som .deb,
    # slik at alle pakker inneholder identisk innhold.
    #
    # Viktig: rpmbuild SLETTER %{_topdir}/SOURCES ved oppstart. Derfor
    # plasseres tarballen i en egen mappe, og _sourcedir pekes dit.
    $srcDir = "/tmp/nimay-omp-rpmsrc-$Version"
    wsl.exe -e sh -lc "rm -rf '$srcDir' && mkdir -p '$srcDir' '$buildDir/src/nimay-omp-bridge-$Version' && cp -r '$wslRoot/payload/bridge' '$buildDir/src/nimay-omp-bridge-$Version/bridge' && cp -r '$wslRoot/scripts' '$buildDir/src/nimay-omp-bridge-$Version/scripts' && cp -r '$wslRoot/payload/tools' '$buildDir/src/nimay-omp-bridge-$Version/tools' && cp -r '$wslRoot/payload/voice' '$buildDir/src/nimay-omp-bridge-$Version/voice' && cp '$wslRoot/payload/nimay-omp-bridge.service' '$buildDir/src/nimay-omp-bridge-$Version/nimay-omp-bridge.service' && tar -czf '$srcDir/$tarName' -C '$buildDir/src' 'nimay-omp-bridge-$Version'" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Kunne ikke lage RPM-kildetarball.' }

    $specPath = Join-Path $linuxRoot 'packaging\rpm\nimay-omp-bridge.spec'
    $specRaw = Get-Content $specPath -Raw -Encoding UTF8
    $specNew = [regex]::Replace($specRaw, '(?m)^Version:\s*\S+', "Version:        $Version")
    [System.IO.File]::WriteAllText($specPath, $specNew, (New-Object System.Text.UTF8Encoding($false)))

    $rpmTop = (wsl.exe -e wslpath -a (Join-Path $linuxRoot 'packaging\rpm')).Trim()
    # rpmbuild skriver mye til stderr; det filtreres bort, slik at
    # PowerShell ikke tar det som en feil. _sourcedir peker pa egen mappe,
    # fordi rpmbuild sletter %{_topdir}/SOURCES ved oppstart.
    wsl.exe -e sh -lc "cd '$rpmTop' && rpmbuild -bb --define '_topdir $rpmTopdir' --define '_sourcedir $srcDir' nimay-omp-bridge.spec >/dev/null 2>&1" | Out-Null

    # Hent ferdigbygde .rpm ut av topdir og legg den i kilden.
    wsl.exe -e sh -lc "find '$rpmTopdir' -name '*.noarch.rpm' -exec cp {} '$rpmTop' \;" | Out-Null
    wsl.exe -e sh -lc "rm -rf '$rpmTopdir' '$srcDir'" | Out-Null

    $rpmFrom = Join-Path (Join-Path $linuxRoot 'packaging\rpm') "nimay-omp-bridge-$Version-1.noarch.rpm"
    $rpmTo = Join-Path $linuxRoot "nimay-omp-bridge-$Version-1.noarch.rpm"
    if (-not (Test-Path $rpmFrom)) { throw "rpmbuild feilet (ingen .rpm ble laget)" }
    Move-Item $rpmFrom $rpmTo -Force
    Write-Host "  + nimay-omp-bridge-$Version-1.noarch.rpm" -ForegroundColor Green
}
finally {
    wsl.exe -e sh -lc "rm -rf '$buildDir' '$rpmTopdir' '/tmp/nimay-omp-rpmsrc-$Version'" | Out-Null
}

Write-Host "Ferdig." -ForegroundColor Green
