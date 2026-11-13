# Install the OmpChat OMP slash commands
#
#   powershell -ExecutionPolicy Bypass -File tools\install_omp_commands.ps1
#
# Copies omp-commands\* into %USERPROFILE%\.omp\agent\commands and writes
# %USERPROFILE%\.omp\agent\ompchat-commands.json so the commands know where the
# OmpChat checkout lives. Existing bridge/config.json is never touched.

param(
    [string]$ProjectRoot = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}
$ProjectRoot = (Resolve-Path $ProjectRoot).Path

$bridgeScript = Join-Path $ProjectRoot "bridge\omp-bridge.mjs"
if (-not (Test-Path $bridgeScript)) {
    throw "Finner ikke $bridgeScript - kjør scriptet fra OmpChat-prosjektet."
}

$commandsRoot = Join-Path $env:USERPROFILE ".omp\agent\commands"
New-Item -ItemType Directory -Force -Path $commandsRoot | Out-Null

foreach ($name in @("ompchat-shared", "ompserver", "ompqr")) {
    $source = Join-Path $ProjectRoot "omp-commands\$name"
    if (-not (Test-Path $source)) {
        throw "Mangler kildekatalog: $source"
    }
    $target = Join-Path $commandsRoot $name
    if (Test-Path $target) {
        Remove-Item -Recurse -Force $target
    }
    Copy-Item -Recurse -Force $source $target
    Write-Host "installert: $target"
}

$stateFile = Join-Path $env:USERPROFILE ".omp\agent\ompchat-commands.json"
$json = @{ projectRoot = $ProjectRoot } | ConvertTo-Json
# UTF-8 without BOM, so JSON.parse in the command modules never sees a BOM.
[System.IO.File]::WriteAllText($stateFile, $json, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "prosjektrot: $ProjectRoot"
Write-Host "state:       $stateFile"

Write-Host ""
Write-Host "Ferdig. Start omp på nytt (kommandoene lastes ved oppstart) og skriv:"
Write-Host "  /ompserver   - start broen i bakgrunnen og vis status"
Write-Host "  /ompqr       - vis parings-QR (Tailscale først)"
Write-Host ""
Write-Host "Krever Node.js 20+ i PATH og Python 3 med 'qrcode' + 'pillow' for /ompqr."
