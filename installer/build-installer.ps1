# Stages the installer payload and compiles the Windows installer with Inno Setup.
#
#   powershell -ExecutionPolicy Bypass -File installer\build-installer.ps1
#
# Output: OmpChat\NimayOMP-Setup-<version>.exe (also kept in installer\dist)
param(
    [string]$Configuration = "release",
    [switch]$SkipStage
)

$ErrorActionPreference = "Stop"
$installerDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $installerDir
$buildRoot = Join-Path $installerDir "build\payload"
$iscc = "C:\Users\Megaxenon\AppData\Local\Programs\Inno Setup 6\ISCC.exe"
if (-not (Test-Path $iscc)) {
    $candidates = @(
        (Join-Path $env:LOCALAPPDATA "Programs\Inno Setup 6\ISCC.exe"),
        "C:\Program Files (x86)\Inno Setup 6\ISCC.exe",
        "C:\Program Files\Inno Setup 6\ISCC.exe"
    )
    $iscc = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
}
if (-not $iscc) { throw "Fant ikke ISCC.exe. Installer Inno Setup: winget install --id JRSoftware.InnoSetup -e" }

$apk = Get-ChildItem (Join-Path $projectRoot "OmpChat-*-release.apk") | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $apk) { throw "Fant ingen OmpChat-*-release.apk i $projectRoot" }

if (-not $SkipStage) {
    Write-Host "Stager payload..."
    if (Test-Path (Join-Path $installerDir "build")) { Remove-Item (Join-Path $installerDir "build") -Recurse -Force }
    New-Item -ItemType Directory -Path (Join-Path $buildRoot "apk"), (Join-Path $buildRoot "bridge") -Force | Out-Null

    Copy-Item $apk.FullName (Join-Path $buildRoot "apk\OmpChat.apk") -Force
    Get-ChildItem (Join-Path $projectRoot "bridge") -Filter "*.mjs" | Copy-Item -Destination (Join-Path $buildRoot "bridge") -Force
    # Bundle node-pty (ConPTY) for the terminal so a fresh install works without npm.
    $nodePtySrc = Join-Path $projectRoot "node_modules\node-pty"
    if (Test-Path $nodePtySrc) {
        $nodePtyDst = Join-Path $buildRoot "bridge\node_modules\node-pty"
        New-Item -ItemType Directory -Path (Join-Path $buildRoot "bridge\node_modules") -Force | Out-Null
        Copy-Item $nodePtySrc $nodePtyDst -Recurse -Force
        Write-Host "  ok  bridge\\node_modules\\node-pty"
    } else {
        Write-Warning "node-pty not found in $nodePtySrc; terminal will fall back to pipes"
    }
    Copy-Item (Join-Path $projectRoot "omp-commands") (Join-Path $buildRoot "commands") -Recurse -Force
    Copy-Item (Join-Path $installerDir "payload\*") $buildRoot -Recurse -Force

    if (-not (Test-Path (Join-Path $buildRoot "tools\node_modules\qrcode"))) {
        Write-Host "Installerer qrcode-pakken for QR-generering..."
        npm install --silent --no-fund --no-audit --prefix (Join-Path $buildRoot "tools") qrcode | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "npm install qrcode feilet" }
    }

    # Sanity checks before compiling.
    $required = @(
        "apk\OmpChat.apk",
        "bridge\omp-bridge.mjs",
        "tools\qrgen.mjs",
        "tools\apk-server.mjs",
        "tools\prepare-mobile-page.ps1",
        "tools\start-bridge-hidden.vbs",
        "voice\setup-voice.ps1",
        "voice\voice_runtime.py",
        "README.txt",
        "Start-OMP-Bridge.cmd"
    )
    foreach ($relative in $required) {
        $path = Join-Path $buildRoot $relative
        if (-not (Test-Path $path)) { throw "Payload mangler: $relative" }
        Write-Host ("  ok  " + $relative)
    }
}

Write-Host "Kompilerer med Inno Setup..."
& $iscc /Qp (Join-Path $installerDir "NimayOMP.iss")
if ($LASTEXITCODE -ne 0) { throw "ISCC feilet med kode $LASTEXITCODE" }

$setup = Get-ChildItem (Join-Path $installerDir "dist\NimayOMP-Setup-*.exe") | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $setup) { throw "Installasjonsfilen ble ikke laget" }

# Remove older setup files from the project root, keep the newest.
Get-ChildItem (Join-Path $projectRoot "NimayOMP-Setup-*.exe") -ErrorAction SilentlyContinue | Remove-Item -Force
$target = Join-Path $projectRoot $setup.Name
Copy-Item $setup.FullName $target -Force

$hash = (Get-FileHash $target -Algorithm SHA256).Hash
Write-Host ""
Write-Host "Ferdig: $target"
Write-Host ("Storrelse: " + [math]::Round($setup.Length / 1MB, 2) + " MB")
Write-Host "SHA-256: $hash"
