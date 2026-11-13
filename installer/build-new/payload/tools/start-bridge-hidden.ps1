# Starts the OMP bridge without a console window and waits until it answers.
# The port comes from bridge\config.json unless -Port is given, so the hidden
# launcher (start-bridge-hidden.vbs) always starts the bridge on the configured
# port.
param(
    [int]$WaitSeconds = 20,
    [int]$Port = 0
)

$appDir = Split-Path -Parent $PSScriptRoot
$bridgeDir = Join-Path $appDir "bridge"
$script = Join-Path $bridgeDir "omp-bridge.mjs"

# Resolve OMP_BIN: env var first, then saved file, then registry.
if (-not $env:OMP_BIN) {
    $binFile = Join-Path $bridgeDir "omp-bin.txt"
    if (Test-Path $binFile) {
        $saved = (Get-Content -Raw -Encoding UTF8 $binFile).Trim()
        if ($saved) { $env:OMP_BIN = $saved }
    }
}
if (-not $env:OMP_BIN) {
    try {
        $reg = (Get-ItemProperty -Path 'HKCU:\Environment' -Name 'OMP_BIN' -ErrorAction SilentlyContinue).OMP_BIN
        if ($reg) { $env:OMP_BIN = $reg }
    } catch { }
}
if (-not (Test-Path $script)) { Write-Host "Fant ikke $script"; exit 1 }

if ($Port -le 0) {
    $Port = 7777
    $configPath = Join-Path $bridgeDir "config.json"
    if (Test-Path $configPath) {
        try {
            $configured = [int](Get-Content -Raw -Encoding UTF8 $configPath | ConvertFrom-Json).port
            if ($configured -gt 0) { $Port = $configured }
        } catch { }
    }
}

$node = (Get-Command node -ErrorAction SilentlyContinue).Source
if (-not $node) {
    foreach ($candidate in @("C:\Program Files\nodejs\node.exe", (Join-Path $env:LOCALAPPDATA "Programs\nodejs\node.exe"))) {
        if (Test-Path $candidate) { $node = $candidate; break }
    }
}
if (-not $node) { Write-Host "Node.js er ikke installert."; exit 1 }

function Test-Bridge {
    try {
        $r = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/api/ping" -TimeoutSec 3 -UseBasicParsing
        return ($r.StatusCode -eq 200)
    } catch { return $false }
}

if (Test-Bridge) { Write-Host "Broen svarer allerede paa port $Port."; exit 0 }

$env:OMPCHAT_BRIDGE_PORT = "$Port"
Start-Process -FilePath $node -ArgumentList "`"$script`"" -WorkingDirectory $bridgeDir -WindowStyle Hidden
for ($i = 0; $i -lt ($WaitSeconds * 2); $i++) {
    Start-Sleep -Milliseconds 500
    if (Test-Bridge) { Write-Host "Broen svarer paa port $Port."; exit 0 }
}
Write-Host "Broen svarte ikke innen $WaitSeconds sekunder. Prov Start-OMP-Bridge.cmd for a se feilen."
exit 1
