# Installs (or refreshes) the global OMP slash commands /ompserver and /ompqr.
# Expects the installer layout: <app>\commands and <app>\bridge\omp-bridge.mjs
param(
    [string]$AppDir = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"
$commandsRoot = Join-Path $env:USERPROFILE ".omp\agent\commands"
New-Item -ItemType Directory -Force -Path $commandsRoot | Out-Null

$installed = @()
foreach ($name in @("ompchat-shared", "ompserver", "ompqr")) {
    $source = Join-Path $AppDir "commands\$name"
    if (-not (Test-Path $source)) { Write-Host "hopper over (mangler): $source"; continue }
    $target = Join-Path $commandsRoot $name
    if (Test-Path $target) { Remove-Item -Recurse -Force $target }
    Copy-Item -Recurse -Force $source $target
    $installed += $name
    Write-Host "installert: $target"
}

$stateFile = Join-Path $env:USERPROFILE ".omp\agent\ompchat-commands.json"
$json = @{ projectRoot = $AppDir; bridge = (Join-Path $AppDir "bridge\omp-bridge.mjs") } | ConvertTo-Json
[System.IO.File]::WriteAllText($stateFile, $json, (New-Object System.Text.UTF8Encoding($false)))
Write-Host ("installerte kommandoer: " + ($installed -join ", "))

